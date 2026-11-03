package labbook_connect.fhir;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;

import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

/**
 * Report of the FHIR facade.
 *
 * Translates the answer of the LabBook service result into one DiagnosticReport
 * per requested analysis, carrying one Observation per result variable. Only
 * the biologically validated results are handed out, LabBook filtering them.
 *
 * The logical id holds the record and the analysis, 312-675, because LabBook
 * reads the results of a whole record while a report answers one analysis.
 * Mapping follows doc/fhir/mapping.md.
 **/
public class DiagnosticReportResourceProvider implements IResourceProvider {

	private static final Logger logger = LoggerFactory.getLogger(DiagnosticReportResourceProvider.class);

	private static final String SERVICE_RESULT = "/result/";

	/** LabBook hands out the validated results only when asked to */
	private static final String VALIDATED_ONLY = "?validated=Y";

	private static final String SYSTEM_LOINC         = "http://loinc.org";
	private static final String SYSTEM_ANALYSIS_CODE = "urn:labbook:analysis-code";
	private static final String SYSTEM_VARIABLE_CODE = "urn:labbook:variable-code";

	private static final String INCLUDE_RESULT  = "DiagnosticReport:result";
	private static final String INCLUDE_SUBJECT = "DiagnosticReport:subject";

	private final LabBookClient client;

	/** Patient provider, so that a report can answer its subject when asked to */
	private final PatientResourceProvider patient;

	/**
	 * @param client Client of the LabBook API.
	 * @param patient Provider that reads a patient from LabBook.
	 */
	public DiagnosticReportResourceProvider(LabBookClient client, PatientResourceProvider patient) {
		this.client  = client;
		this.patient = patient;
	}

	@Override
	public Class<DiagnosticReport> getResourceType() {
		return DiagnosticReport.class;
	}

	/**
	 * Reads one report by its logical id, the record and the analysis.
	 * @param theId Logical id, record id, a dash, analysis id.
	 * @return The report, its results held as contained-free references.
	 */
	@Read
	public DiagnosticReport read(@IdParam IdType theId) {
		Key        key     = Key.of(theId);
		JsonObject results = resultsOf(key.record, theId);

		for (JsonObject analysis : analysesOf(results)) {
			if (key.analysis.equals(jsonField(analysis, "id_ana"))) {
				return toReport(key, analysis, patientReference(results), new ArrayList<>());
			}
		}

		throw new ResourceNotFoundException(theId);
	}

	/**
	 * Searches reports by their logical id, answering the results in the same
	 * Bundle when _include asks for them, which is how an outside system reads
	 * a report and its values in one call.
	 * @param id Logical id of the report.
	 * @param includes Includes asked for, DiagnosticReport:result being the one that counts.
	 * @return The report, and its results when they were asked for.
	 */
	@Search
	public List<IBaseResource> search(
			@OptionalParam(name = "_id") TokenParam id,
			@IncludeParam(allow = { INCLUDE_RESULT, INCLUDE_SUBJECT }) Set<Include> includes) {

		List<IBaseResource> found = new ArrayList<>();

		if (id == null || id.getValue() == null) {
			return found;
		}

		IdType theId = new IdType(id.getValue());
		Key    key   = Key.of(theId);

		boolean withResults = asked(includes, INCLUDE_RESULT);
		boolean withPatient = asked(includes, INCLUDE_SUBJECT);

		JsonObject answer = resultsOf(key.record, theId);

		for (JsonObject analysis : analysesOf(answer)) {
			if (!key.analysis.equals(jsonField(analysis, "id_ana"))) {
				continue;
			}

			List<Observation> results = new ArrayList<>();

			found.add(toReport(key, analysis, patientReference(answer), results));

			if (withResults) {
				found.addAll(results);
			}

			/**
			 * The resource is answered only when the reference is the LabBook code,
			 * the one our Patient provider knows how to read. When it is the id of
			 * the calling system, that system already holds the patient.
			 */
			if (withPatient) {
				String code = jsonField(answer, "patient_code");

				if (!code.isEmpty() && code.equals(patientReference(answer))) {
					found.add(patient.read(new IdType("Patient", code)));
				}
			}
		}

		logger.info("Report {} read, {} resources answered", id.getValue(), found.size());

		return found;
	}

	/**
	 * Returns the patient reference to put on a report and on its results. The
	 * calling system only answers to the id of its own Patient resource, so that
	 * one comes first, and the LabBook code is the fallback.
	 */
	private String patientReference(JsonObject results) {
		for (JsonObject reference : jsonArray(results, "patient_resource")) {
			String ref = jsonField(reference, "ref");

			if (!ref.isEmpty()) {
				return ref;
			}
		}

		return jsonField(results, "patient_code");
	}

	/**
	 * Reads an array of objects, empty when absent.
	 */
	private List<JsonObject> jsonArray(JsonObject row, String field) {
		JsonValue value = row.get(field);

		if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
			return new ArrayList<>();
		}

		return value.asJsonArray().getValuesAs(JsonObject.class);
	}

	/**
	 * Tells whether one include was asked for.
	 */
	private boolean asked(Set<Include> includes, String name) {
		return includes != null && includes.stream()
				.anyMatch(include -> name.equals(include.getValue()));
	}

	/**
	 * Calls the LabBook result service and returns what it answered.
	 */
	private JsonObject resultsOf(String record, IdType theId) {
		LabBookClient.Answer answer =
				client.getFromLabBookWithStatus(SERVICE_RESULT + record + VALIDATED_ONLY);

		switch (answer.status()) {
			case 200:
				break;
			case 404:
				throw new ResourceNotFoundException(theId);
			default:
				throw new InternalErrorException(
						"LabBook answered HTTP " + answer.status() + " on " + SERVICE_RESULT + record);
		}

		try (JsonReader reader = Json.createReader(new StringReader(answer.body()))) {
			JsonValue value = reader.readValue();

			if (value.getValueType() != JsonValue.ValueType.OBJECT) {
				throw new ResourceNotFoundException(theId);
			}

			return value.asJsonObject();
		}
	}

	/**
	 * Returns the analyses held by one answer of the result service.
	 */
	private List<JsonObject> analysesOf(JsonObject results) {
		JsonValue analyses = results.get("analysis");

		if (analyses == null || analyses.getValueType() != JsonValue.ValueType.ARRAY) {
			return new ArrayList<>();
		}

		return analyses.asJsonArray().getValuesAs(JsonObject.class);
	}

	/**
	 * Builds the report of one analysis, filling the given list with its results.
	 */
	private DiagnosticReport toReport(Key key, JsonObject analysis, String patientCode,
			List<Observation> results) {
		DiagnosticReport report = new DiagnosticReport();

		report.setId(key.id());
		report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

		report.setCode(concept(jsonField(analysis, "loinc"), jsonField(analysis, "ana_code"),
				jsonField(analysis, "name"), SYSTEM_ANALYSIS_CODE));

		if (!patientCode.isEmpty()) {
			report.setSubject(new Reference("Patient/" + patientCode));
		}

		JsonValue variables = analysis.get("variables");

		if (variables == null || variables.getValueType() != JsonValue.ValueType.ARRAY) {
			return report;
		}

		String issued = "";

		for (JsonObject variable : variables.asJsonArray().getValuesAs(JsonObject.class)) {
			Observation observation = toObservation(key, variable, patientCode);

			results.add(observation);
			report.addResult(new Reference("Observation/" + observation.getIdElement().getIdPart()));

			String validated = jsonField(variable, "validated_on");

			/** The report is issued when its last result was validated */
			if (validated.compareTo(issued) > 0) {
				issued = validated;
			}
		}

		if (!issued.isEmpty()) {
			report.setIssuedElement(new org.hl7.fhir.r4.model.InstantType(issued.replace(' ', 'T')));
		}

		return report;
	}

	/**
	 * Builds the result of one variable.
	 */
	private Observation toObservation(Key key, JsonObject variable, String patientCode) {
		Observation observation = new Observation();

		observation.setId(jsonField(variable, "id_res"));
		observation.setStatus(Observation.ObservationStatus.FINAL);

		if (!patientCode.isEmpty()) {
			observation.setSubject(new Reference("Patient/" + patientCode));
		}

		observation.setCode(concept(jsonField(variable, "loinc"), jsonField(variable, "code_var"),
				jsonField(variable, "label"), SYSTEM_VARIABLE_CODE));

		String value = jsonField(variable, "value");
		String unit  = jsonField(variable, "unit");

		/** A number becomes a quantity, anything else stays the text the laboratory wrote */
		if (value.matches("-?\\d+([.,]\\d+)?")) {
			Quantity quantity = new Quantity();

			quantity.setValue(new java.math.BigDecimal(value.replace(',', '.')));

			if (!unit.isEmpty()) {
				quantity.setUnit(unit);
			}

			observation.setValue(quantity);
		} else if (!value.isEmpty()) {
			observation.setValue(new org.hl7.fhir.r4.model.StringType(value));
		}

		addRange(observation, jsonField(variable, "normal_min"), jsonField(variable, "normal_max"), unit);
		addInterpretation(observation, jsonField(variable, "interpretation"));

		String validated = jsonField(variable, "validated_on");

		if (!validated.isEmpty()) {
			observation.setEffective(new org.hl7.fhir.r4.model.DateTimeType(validated.replace(' ', 'T')));
		}

		return observation;
	}

	/**
	 * Adds the reference range of the laboratory, left out when a bound is missing.
	 */
	private void addRange(Observation observation, String low, String high, String unit) {
		if (low.isEmpty() || high.isEmpty()) {
			return;
		}

		Observation.ObservationReferenceRangeComponent range = observation.addReferenceRange();

		range.setLow(bound(low, unit));
		range.setHigh(bound(high, unit));
	}

	/**
	 * Builds one bound of a reference range.
	 */
	private org.hl7.fhir.r4.model.SimpleQuantity bound(String value, String unit) {
		org.hl7.fhir.r4.model.SimpleQuantity quantity = new org.hl7.fhir.r4.model.SimpleQuantity();

		quantity.setValue(new java.math.BigDecimal(value.replace(',', '.')));

		if (!unit.isEmpty()) {
			quantity.setUnit(unit);
		}

		return quantity;
	}

	/**
	 * Adds the interpretation worked out by LabBook, in the codes HL7 holds for it.
	 */
	private void addInterpretation(Observation observation, String interpretation) {
		String code;

		switch (interpretation) {
			case "low":
				code = "L";
				break;
			case "high":
				code = "H";
				break;
			case "normal":
				code = "N";
				break;
			default:
				return;
		}

		observation.addInterpretation()
				.addCoding()
				.setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
				.setCode(code);
	}

	/**
	 * Builds a coded concept, the LOINC code first when LabBook holds one, then
	 * the LabBook code, and the label as free text.
	 */
	private CodeableConcept concept(String loinc, String code, String label, String codeSystem) {
		CodeableConcept concept = new CodeableConcept();

		if (!loinc.isEmpty()) {
			concept.addCoding().setSystem(SYSTEM_LOINC).setCode(loinc);
		}

		if (!code.isEmpty()) {
			concept.addCoding().setSystem(codeSystem).setCode(code);
		}

		if (!label.isEmpty()) {
			concept.setText(label);
		}

		return concept;
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

	/**
	 * The two parts of the logical id of a report, the record and the analysis.
	 */
	private record Key(String record, String analysis) {

		/**
		 * Reads a logical id written record-analysis.
		 */
		static Key of(IdType theId) {
			String[] parts = theId.getIdPart().split("-");

			if (parts.length != 2 || !parts[0].matches("\\d+") || !parts[1].matches("\\d+")) {
				throw new ResourceNotFoundException(theId);
			}

			return new Key(parts[0], parts[1]);
		}

		String id() {
			return record + "-" + analysis;
		}
	}
}
