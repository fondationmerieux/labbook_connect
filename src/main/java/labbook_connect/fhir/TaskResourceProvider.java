package labbook_connect.fhir;

import java.io.StringReader;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

/**
 * Record request of the FHIR facade, read side.
 *
 * Translates the answer of the LabBook service record/det into a FHIR R4 Task,
 * the work order the outside system follows to know where its request stands.
 * The logical id is the LabBook record id, the one the creation sent back in
 * the location of its answer. Mapping follows doc/fhir/mapping.md.
 **/
public class TaskResourceProvider implements IResourceProvider {

	private static final Logger logger = LoggerFactory.getLogger(TaskResourceProvider.class);

	private static final String SERVICE_RECORD_DET     = "/record/det/";
	private static final String SERVICE_RECORD_UPDATED = "/record/updated";

	/** Date LabBook reads, and the floor of a search sent without one */
	private static final DateTimeFormatter LABBOOK_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final String            DATE_FLOOR   = "1970-01-01 00:00:00";

	/** Identifier system of the record number given by the laboratory */
	private static final String SYSTEM_RECORD_NUMBER = "urn:labbook:record-number";

	/** Prefix making a system out of the short name LabBook keeps for a calling system */
	private static final String SYSTEM_EXTERNAL_PREFIX = "urn:labbook:external:";

	private final LabBookClient client;

	/**
	 * @param client Client of the LabBook API.
	 */
	public TaskResourceProvider(LabBookClient client) {
		this.client = client;
	}

	@Override
	public Class<Task> getResourceType() {
		return Task.class;
	}

	/**
	 * Reads one record request by the LabBook record id.
	 * @param theId Logical id, the LabBook record id.
	 * @return The record request as a FHIR resource.
	 */
	@Read
	public Task read(@IdParam IdType theId) {
		String id = theId.getIdPart();

		if (!id.matches("\\d+")) {
			throw new ResourceNotFoundException(theId);
		}

		LabBookClient.Answer answer = client.getFromLabBookWithStatus(SERVICE_RECORD_DET + id);

		switch (answer.status()) {
			case 200:
				break;
			case 404:
				throw new ResourceNotFoundException(theId);
			default:
				throw new InternalErrorException(
						"LabBook answered HTTP " + answer.status() + " on " + SERVICE_RECORD_DET + id);
		}

		return toTask(readObject(answer.body(), theId));
	}

	/**
	 * Searches the record requests changed since a date, the way an outside system
	 * asks for what has moved since its last call.
	 * @param lastUpdated Range whose lower bound is the date of the last call.
	 * @param status Task status, filtered here because LabBook knows nothing of it.
	 * @return The matching record requests, oldest change first.
	 */
	@Search
	public List<Task> search(
			@OptionalParam(name = ca.uhn.fhir.rest.api.Constants.PARAM_LASTUPDATED) DateRangeParam lastUpdated,
			@OptionalParam(name = Task.SP_STATUS) TokenOrListParam status,
			@OptionalParam(name = Task.SP_IDENTIFIER) TokenParam identifier) {

		JsonObject filter = Json.createObjectBuilder()
				.add("date_from", lowerBound(lastUpdated))
				.add("system", client.externalSystem())
				.build();

		String answer = client.postToLabBook(SERVICE_RECORD_UPDATED, filter.toString());

		List<Task> tasks = new ArrayList<>();

		for (JsonObject row : readArray(answer)) {
			Task task = toTask(row);

			if (!keptByStatus(task, status) || !keptByUpperBound(task, lastUpdated)) {
				continue;
			}

			tasks.add(task);
		}

		logger.info("{} record requests answered, changed since {}", tasks.size(),
				filter.getString("date_from"));

		return tasks;
	}

	/**
	 * Tells whether the task carries one of the asked statuses. An outside system
	 * asks for several at once, and asking for none keeps them all.
	 */
	private boolean keptByStatus(Task task, TokenOrListParam status) {
		if (status == null || status.getValuesAsQueryTokens().isEmpty()) {
			return true;
		}

		for (TokenParam asked : status.getValuesAsQueryTokens()) {
			if (asked.getValue() != null && asked.getValue().equals(task.getStatus().toCode())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Tells whether the task changed before the upper bound of the asked range.
	 * LabBook filters on the lower bound only, so this one is held here.
	 */
	private boolean keptByUpperBound(Task task, DateRangeParam lastUpdated) {
		if (lastUpdated == null || lastUpdated.getUpperBoundAsInstant() == null
				|| !task.hasLastModified()) {
			return true;
		}

		return !task.getLastModified().after(lastUpdated.getUpperBoundAsInstant());
	}

	/**
	 * Returns the lower bound of the asked range as LabBook writes a date, the
	 * floor when the search carries none.
	 */
	private String lowerBound(DateRangeParam lastUpdated) {
		if (lastUpdated == null || lastUpdated.getLowerBoundAsInstant() == null) {
			return DATE_FLOOR;
		}

		return LABBOOK_DATE.format(lastUpdated.getLowerBoundAsInstant().toInstant()
				.atZone(ZoneId.systemDefault()));
	}

	/**
	 * Reads the answer of LabBook as a list of records, empty when it sent none.
	 */
	private List<JsonObject> readArray(String answer) {
		try (JsonReader reader = Json.createReader(new StringReader(answer))) {
			JsonValue value = reader.readValue();

			if (value.getValueType() != JsonValue.ValueType.ARRAY) {
				return new ArrayList<>();
			}

			return value.asJsonArray().getValuesAs(JsonObject.class);
		}
	}

	/**
	 * Reads the answer of LabBook as one JSON object.
	 */
	private JsonObject readObject(String answer, IdType theId) {
		try (JsonReader reader = Json.createReader(new StringReader(answer))) {
			JsonValue value = reader.readValue();

			/** LabBook answers null or an empty body when it found nothing */
			if (value.getValueType() != JsonValue.ValueType.OBJECT) {
				throw new ResourceNotFoundException(theId);
			}

			return value.asJsonObject();
		}
	}

	/**
	 * Builds the Task from one record of LabBook.
	 */
	private Task toTask(JsonObject row) {
		Task task = new Task();

		task.setId(jsonField(row, "rec_id"));

		task.setStatus(toStatus(jsonField(row, "rec_status")));
		task.setIntent(Task.TaskIntent.ORDER);

		/**
		 * The reference of the calling system is what it recognises its own request by.
		 * LabBook keeps a short name for that system, FHIR asks for a URI.
		 */
		for (JsonObject reference : jsonArray(row, "external")) {
			setIdentifier(task, SYSTEM_EXTERNAL_PREFIX + jsonField(reference, "system"),
					jsonField(reference, "ref"));
		}

		/** The record number of the laboratory, which the calling system never gave */
		setIdentifier(task, SYSTEM_RECORD_NUMBER, jsonField(row, "rec_num"));

		JsonObject patient = row.getJsonObject("patient");

		if (patient != null) {
			String code = jsonField(patient, "pat_code");

			if (!code.isEmpty()) {
				task.setFor(new Reference("Patient/" + code));
			}
		}

		setDate(task::setAuthoredOnElement, jsonField(row, "rec_date_prescr"));
		setDate(task::setLastModifiedElement, jsonField(row, "rec_date_status"));

		/**
		 * The reports, one per requested analysis, named only once the record is
		 * biologically validated, before that LabBook hands out no result.
		 */
		if (task.getStatus() == Task.TaskStatus.COMPLETED) {
			String record = jsonField(row, "rec_id");

			for (JsonObject analysis : jsonArray(row, "ana_list")) {
				String id = record + "-" + jsonField(analysis, "ana_req");

				task.addOutput()
						.setType(new org.hl7.fhir.r4.model.CodeableConcept().setText("Report"))
						.setValue(new Reference("DiagnosticReport/" + id));
			}
		}

		logger.info("Record {} read as a FHIR Task, status {}", jsonField(row, "rec_id"), task.getStatus());

		return task;
	}

	/**
	 * Translates the LabBook record status into a FHIR task status. Only the
	 * biological validation makes the task complete, before that the results
	 * are not handed out.
	 */
	private Task.TaskStatus toStatus(String status) {
		switch (status) {
			case "N":
				return Task.TaskStatus.RECEIVED;
			case "A":
				return Task.TaskStatus.ACCEPTED;
			case "IA":
			case "T":
			case "IT":
				return Task.TaskStatus.INPROGRESS;
			case "B":
				return Task.TaskStatus.COMPLETED;
			default:
				logger.warn("LabBook record status {} has no FHIR equivalent", status);
				return Task.TaskStatus.NULL;
		}
	}

	/**
	 * Adds an identifier only when LabBook holds a value for it.
	 */
	private void setIdentifier(Task task, String system, String value) {
		if (!value.isEmpty()) {
			task.addIdentifier().setSystem(system).setValue(value);
		}
	}

	/**
	 * Sets a date only when LabBook holds one, LabBook writing a space where
	 * FHIR expects a T between the date and the hour.
	 */
	private void setDate(java.util.function.Consumer<org.hl7.fhir.r4.model.DateTimeType> setter, String value) {
		if (value.isEmpty()) {
			return;
		}

		setter.accept(new org.hl7.fhir.r4.model.DateTimeType(value.replace(' ', 'T')));
	}

	/**
	 * Reads an array of objects, empty when absent.
	 */
	private List<JsonObject> jsonArray(JsonObject row, String field) {
		JsonValue value = row.get(field);

		if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
			return new ArrayList<>();
		}

		JsonArray array = value.asJsonArray();

		return array.getValuesAs(JsonObject.class);
	}

	/**
	 * Reads a field as text, whatever type LabBook used, empty when absent.
	 */
	private String jsonField(JsonObject row, String field) {
		JsonValue value = row.get(field);

		if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
			return "";
		}

		if (value.getValueType() == JsonValue.ValueType.STRING) {
			return row.getString(field, "");
		}

		return value.toString();
	}
}
