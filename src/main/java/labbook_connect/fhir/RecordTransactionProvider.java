package labbook_connect.fhir;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;

import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

/**
 * Record creation of the FHIR facade.
 *
 * Takes a transaction Bundle holding the Patient, the Practitioner, one
 * ServiceRequest per requested analysis and the Task that groups them, and
 * calls the LabBook service record/det to create the record. Mapping follows
 * doc/fhir/mapping.md.
 *
 * Samples are not exchanged: LabBook creates the default sample of each
 * analysis from its own settings.
 **/
public class RecordTransactionProvider {

	private static final Logger logger = LoggerFactory.getLogger(RecordTransactionProvider.class);

	private static final String SERVICE_RECORD_DET = "/record/det/0";

	/** Identifier systems of LabBook itself, never those of the calling system */
	private static final String SYSTEM_CODE     = "urn:labbook:patient-code";
	private static final String SYSTEM_CODE_LAB = "urn:labbook:patient-code-lab";

	private static final String SYSTEM_LOINC = "http://loinc.org";

	/** System a caller declares when it knows an analysis by its LabBook code */
	private static final String SYSTEM_ANALYSIS = "urn:labbook:analysis-code";

	/** Formats LabBook expects on the LabBook API */
	private static final DateTimeFormatter FORMAT_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
	private static final DateTimeFormatter FORMAT_DATE     = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final LabBookClient client;

	/**
	 * @param client Client of the LabBook API.
	 */
	public RecordTransactionProvider(LabBookClient client) {
		this.client = client;
	}

	/**
	 * Creates one LabBook record from a transaction Bundle.
	 * @param bundle The Patient, the Practitioner, the ServiceRequests and the Task.
	 * @return A transaction-response Bundle naming the created record.
	 */
	@Transaction
	public Bundle createRecord(@TransactionParam Bundle bundle) {
		Patient                patient  = null;
		Practitioner           doctor   = null;
		Task                   task     = null;
		List<ServiceRequest>   requests = new ArrayList<>();
		int                    closed   = 0;

		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			Resource resource = entry.getResource();

			if (resource instanceof Patient found) {
				patient = found;
			} else if (resource instanceof Practitioner found) {
				doctor = found;
			} else if (resource instanceof Task found) {
				task = found;
			} else if (resource instanceof ServiceRequest found) {
				/**
				 * Only a living request becomes an analysis. A cancelled one comes back with
				 * another status, and OpenMRS sends unknown where the standard asks for revoked.
				 * Cancelling the record of a request already created is the next step.
				 */
				if (found.getStatus() == ServiceRequest.ServiceRequestStatus.ACTIVE) {
					requests.add(found);
				} else {
					closed++;
					logger.info("ServiceRequest {} ignored, its status is {}", found.getIdElement().getIdPart(),
							found.getStatus());
				}
			}
		}

		if (patient == null) {
			throw new InvalidRequestException("The Bundle holds no Patient, LabBook cannot create a record without one");
		}

		if (requests.isEmpty() && closed > 0) {
			throw new InvalidRequestException("Every ServiceRequest of the Bundle is cancelled or closed, no record is created");
		}

		if (requests.isEmpty()) {
			throw new InvalidRequestException("The Bundle holds no ServiceRequest, a record needs at least one analysis");
		}

		JsonObject body = Json.createObjectBuilder()
				.add("record",    recordBlock(task, requests))
				.add("patient",   patientBlock(patient))
				.add("prescriber", prescriberBlock(doctor))
				.add("external",  externalBlock(task, patient))
				.add("ana_list",  analysisBlock(requests))
				.add("samp_list", Json.createArrayBuilder())
				.build();

		LabBookClient.Answer answer = client.postToLabBookWithStatus(SERVICE_RECORD_DET, body.toString());

		return switch (answer.status()) {
			case 200 -> created(answer.body(), requests.size());
			case 409 -> throw new ResourceVersionConflictException(
					"This request has already been received by LabBook : " + reason(answer.body()));
			case 400 -> throw new InvalidRequestException(
					"LabBook refused the request : " + reason(answer.body()));
			default  -> throw new InternalErrorException(
					"LabBook answered HTTP " + answer.status() + " on " + SERVICE_RECORD_DET);
		};
	}

	/**
	 * Builds the transaction-response Bundle once LabBook created the record.
	 */
	private Bundle created(String answer, int count) {
		String id = answer.trim();

		logger.info("Record {} created in LabBook from a FHIR transaction, {} analyses", id, count);

		Bundle response = new Bundle();

		response.setType(Bundle.BundleType.TRANSACTIONRESPONSE);

		Bundle.BundleEntryComponent entry = response.addEntry();

		entry.getResponse()
				.setStatus("201 Created")
				.setLocation("Task/" + id);

		return response;
	}

	/**
	 * Returns the reason LabBook gave for a refusal, or its whole answer.
	 */
	private String reason(String answer) {
		try (JsonReader reader = Json.createReader(new StringReader(answer))) {
			JsonValue value = reader.readValue();

			if (value.getValueType() == JsonValue.ValueType.OBJECT) {
				JsonObject object = value.asJsonObject();

				if (object.containsKey("reason")) {
					return object.getString("reason", "");
				}

				if (object.containsKey("error")) {
					return object.getString("error", "");
				}
			}
		} catch (Exception e) {
			logger.warn("Unreadable refusal from LabBook: {}", answer);
		}

		return answer;
	}

	/**
	 * The record part. Every field LabBook reads is sent, defaults included,
	 * because the service reads them without checking they are there.
	 */
	private JsonObjectBuilder recordBlock(Task task, List<ServiceRequest> requests) {
		String prescribed = "";

		if (task != null && task.hasAuthoredOn()) {
			prescribed = date(task.getAuthoredOn());
		} else {
			for (ServiceRequest request : requests) {
				if (request.hasAuthoredOn()) {
					prescribed = date(request.getAuthoredOn());
					break;
				}
			}
		}

		String comment = "";

		if (task != null && task.hasNote() && task.getNoteFirstRep().hasText()) {
			comment = task.getNoteFirstRep().getText();
		}

		/** A request coming from outside is always of type External */
		return Json.createObjectBuilder()
				.add("rec_type",             "E")
				.add("rec_date",             LocalDateTime.now().format(FORMAT_DATETIME))
				.add("rec_date_prescr",      prescribed)
				.add("rec_num_int",          "")
				.add("rec_custody",          "N")
				.add("rec_comm",             comment)
				.add("rec_parcel_id",        "")
				.add("rec_parcel_date",      "")
				.add("rec_invoice_discount", "")
				.add("rec_percent_discount", 0)
				.add("rec_insurance_cover",  0)
				.add("rec_receipt_num",      "")
				.add("rec_date_hosp",        "")
				.add("rec_int_service",      "")
				.add("rec_bed_num",          0)
				.add("rec_hosp_num",         "");
	}

	/**
	 * The patient part, from the Patient resource.
	 */
	private JsonObjectBuilder patientBlock(Patient patient) {
		HumanName official = name(patient, HumanName.NameUse.OFFICIAL);
		HumanName maiden   = name(patient, HumanName.NameUse.MAIDEN);

		Address address = patient.hasAddress() ? patient.getAddressFirstRep() : new Address();

		List<String> phones = contacts(patient.getTelecom(), ContactPoint.ContactPointSystem.PHONE);

		String sex = "U";

		if (patient.hasGender()) {
			switch (patient.getGender()) {
				case MALE   -> sex = "M";
				case FEMALE -> sex = "F";
				default     -> sex = "U";
			}
		}

		return Json.createObjectBuilder()
				.add("pat_code",         identifier(patient.getIdentifier(), SYSTEM_CODE))
				.add("pat_code_lab",     identifier(patient.getIdentifier(), SYSTEM_CODE_LAB))
				.add("pat_anonymous",    "N")
				.add("pat_name",         official.hasFamily() ? official.getFamily() : "")
				.add("pat_middle_name",  given(official, 1))
				.add("pat_maiden_name",  maiden.hasFamily() ? maiden.getFamily() : "")
				.add("pat_firstname",    given(official, 0))
				.add("pat_sex",          sex)
				.add("pat_birth",        patient.hasBirthDate() ? date(patient.getBirthDate()) : "")
				.add("pat_date_approx",  "N")
				.add("pat_age",          0)
				.add("pat_age_unit",     "Y")
				.add("pat_address",      address.hasLine() ? String.join(" ", address.getLine().stream().map(l -> l.getValue()).toList()) : "")
				.add("pat_pbox",         "")
				.add("pat_area",         address.hasDistrict() ? address.getDistrict() : "")
				.add("pat_zipcode",      address.hasPostalCode() ? address.getPostalCode() : "")
				.add("pat_city",         address.hasCity() ? address.getCity() : "")
				.add("pat_phone1",       phones.size() > 0 ? phones.get(0) : "")
				.add("pat_phone2",       phones.size() > 1 ? phones.get(1) : "")
				.add("pat_job",          "")
				.add("pat_nationality",  "")
				.add("pat_resident",     "")
				.add("pat_blood_group",  "")
				.add("pat_blood_rhesus", "");
	}

	/**
	 * The prescriber part. An absent Practitioner gives an empty block, which
	 * LabBook accepts: the record then has no prescriber.
	 */
	private JsonObjectBuilder prescriberBlock(Practitioner doctor) {
		if (doctor == null) {
			doctor = new Practitioner();
		}

		HumanName official = doctor.hasName() ? doctor.getNameFirstRep() : new HumanName();

		Address address = doctor.hasAddress() ? doctor.getAddressFirstRep() : new Address();

		/**
		 * The code of a prescriber is a laboratory code of seven characters, so the
		 * identifier of the calling system has no place in it. It stays empty, and
		 * LabBook recognises the prescriber on its name and its first name.
		 */
		return Json.createObjectBuilder()
				.add("prescr_code",      "")
				.add("prescr_title",     official.hasPrefix() ? official.getPrefixAsSingleString() : "")
				.add("prescr_name",      official.hasFamily() ? official.getFamily() : "")
				.add("prescr_firstname", given(official, 0))
				.add("prescr_initial",   "")
				.add("prescr_workplace", "")
				.add("prescr_service",   "")
				.add("prescr_address",   address.hasLine() ? String.join(" ", address.getLine().stream().map(l -> l.getValue()).toList()) : "")
				.add("prescr_zipcode",   address.hasPostalCode() ? address.getPostalCode() : "")
				.add("prescr_city",      address.hasCity() ? address.getCity() : "")
				.add("prescr_spe",       "")
				.add("prescr_phone",     contact(doctor.getTelecom(), ContactPoint.ContactPointSystem.PHONE, ContactPoint.ContactPointUse.WORK))
				.add("prescr_mobile",    contact(doctor.getTelecom(), ContactPoint.ContactPointSystem.PHONE, ContactPoint.ContactPointUse.MOBILE))
				.add("prescr_fax",       contact(doctor.getTelecom(), ContactPoint.ContactPointSystem.FAX, null))
				.add("prescr_email",     contact(doctor.getTelecom(), ContactPoint.ContactPointSystem.EMAIL, null));
	}

	/**
	 * The references of the calling system, kept apart from the laboratory data.
	 * The request reference is looked for in the identifiers of the Task first,
	 * then in its resource id : OpenMRS leaves the identifiers empty and only
	 * sends the uuid of its own request.
	 */
	private JsonObjectBuilder externalBlock(Task task, Patient patient) {
		String system   = "";
		String record   = "";
		String external = "";

		for (Identifier id : patient.getIdentifier()) {
			if (id.hasValue() && id.hasSystem() && !isLabBookSystem(id.getSystem())) {
				system   = id.getSystem();
				external = id.getValue();
				break;
			}
		}

		if (task != null) {
			for (Identifier id : task.getIdentifier()) {
				if (id.hasValue() && id.hasSystem() && !isLabBookSystem(id.getSystem())) {
					system = id.getSystem();
					record = id.getValue();
					break;
				}
			}

			/** No identifier given, the resource id is the reference of the calling system */
			if (record.isEmpty() && task.getIdElement() != null && task.getIdElement().getIdPart() != null) {
				record = task.getIdElement().getIdPart();
			}
		}

		/** LabBook keeps a short name, the URL of the FHIR system stays in the messages */
		if (!client.externalSystem().isEmpty()) {
			system = client.externalSystem();
		}

		return Json.createObjectBuilder()
				.add("system",  system)
				.add("record",  record)
				.add("patient", external);
	}

	/**
	 * One entry per requested analysis. The LOINC code is sent along with the
	 * LabBook code, so the laboratory recognises the analysis either way.
	 */
	private JsonArrayBuilder analysisBlock(List<ServiceRequest> requests) {
		JsonArrayBuilder list = Json.createArrayBuilder();

		for (ServiceRequest request : requests) {
			String code  = "";
			String loinc = "";

			if (request.hasCode()) {
				for (Coding coding : request.getCode().getCoding()) {
					if (!coding.hasCode()) {
						continue;
					}

					if (SYSTEM_LOINC.equals(coding.getSystem())) {
						loinc = coding.getCode();
					} else if (code.isEmpty() && SYSTEM_ANALYSIS.equals(coding.getSystem())) {
						/** A code of another system is never a LabBook code, the LOINC is then the only reference */
						code = coding.getCode();
					}
				}
			}

			String urgent = "N";

			if (request.hasPriority()) {
				switch (request.getPriority()) {
					case URGENT, ASAP, STAT -> urgent = "Y";
					default                 -> urgent = "N";
				}
			}

			list.add(Json.createObjectBuilder()
					.add("ana_code",  code)
					.add("ana_loinc", loinc)
					.add("ana_emer",  urgent)
					.add("ana_req",   "Y")
					.add("ana_out",   "N"));
		}

		return list;
	}

	/**
	 * Tells whether a system belongs to LabBook, so the facade does not take a
	 * laboratory code for an external reference.
	 */
	private boolean isLabBookSystem(String system) {
		return SYSTEM_CODE.equals(system) || SYSTEM_CODE_LAB.equals(system);
	}

	/**
	 * Returns the value of the identifier of the given system, or of the first
	 * one when no system is asked for.
	 */
	private String identifier(List<Identifier> identifiers, String system) {
		for (Identifier id : identifiers) {
			if (!id.hasValue()) {
				continue;
			}

			if (system.isEmpty() || system.equals(id.getSystem())) {
				return id.getValue();
			}
		}

		return "";
	}

	/**
	 * Returns the name of the asked use, empty when the resource has none.
	 */
	private HumanName name(Patient patient, HumanName.NameUse use) {
		for (HumanName name : patient.getName()) {
			if (use.equals(name.getUse())) {
				return name;
			}
		}

		/** LabBook has one name only, so a name with no use is taken as the official one */
		if (HumanName.NameUse.OFFICIAL.equals(use)) {
			for (HumanName name : patient.getName()) {
				if (!name.hasUse()) {
					return name;
				}
			}
		}

		return new HumanName();
	}

	/**
	 * Returns one of the given names, empty when there are fewer than that.
	 */
	private String given(HumanName name, int position) {
		return name.getGiven().size() > position ? name.getGiven().get(position).getValue() : "";
	}

	/**
	 * Returns the first contact of the asked system and use, any use when none
	 * is asked for.
	 */
	private String contact(List<ContactPoint> telecom, ContactPoint.ContactPointSystem system,
			ContactPoint.ContactPointUse use) {
		for (ContactPoint point : telecom) {
			if (!point.hasValue() || !system.equals(point.getSystem())) {
				continue;
			}

			if (use == null || use.equals(point.getUse())) {
				return point.getValue();
			}
		}

		return "";
	}

	/**
	 * Returns every contact of the asked system, in the order they came.
	 */
	private List<String> contacts(List<ContactPoint> telecom, ContactPoint.ContactPointSystem system) {
		List<String> values = new ArrayList<>();

		for (ContactPoint point : telecom) {
			if (point.hasValue() && system.equals(point.getSystem())) {
				values.add(point.getValue());
			}
		}

		return values;
	}

	/**
	 * Formats a date the way the LabBook API expects it.
	 */
	private String date(Date value) {
		return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(FORMAT_DATE);
	}
}
