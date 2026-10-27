package labbook_connect.fhir;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

/**
 * Patient of the FHIR facade.
 *
 * Translates the answer of the LabBook service patient/list into a FHIR R4
 * Patient. The LabBook patient code is used as the logical id, because it is
 * the only field patient/list can filter on. Mapping follows
 * doc/fhir/mapping.md.
 **/
public class PatientResourceProvider implements IResourceProvider {

	private static final Logger logger = LoggerFactory.getLogger(PatientResourceProvider.class);

	private static final String SERVICE_PATIENT_LIST = "/patient/list";

	/** Identifier systems, provisional until the Implementation Guide fixes them */
	private static final String SYSTEM_CODE     = "urn:labbook:patient-code";
	private static final String SYSTEM_CODE_LAB = "urn:labbook:patient-code-lab";

	private final LabBookClient client;

	/**
	 * @param client Client of the LabBook API.
	 */
	public PatientResourceProvider(LabBookClient client) {
		this.client = client;
	}

	@Override
	public Class<Patient> getResourceType() {
		return Patient.class;
	}

	/**
	 * Reads one patient by its LabBook code.
	 * @param theId Logical id, the LabBook patient code.
	 * @return The patient as a FHIR resource.
	 */
	@Read
	public Patient read(@IdParam IdType theId) {
		String code = theId.getIdPart();

		JsonObject filter = Json.createObjectBuilder().add("code", code).build();

		/** LabBook filters with LIKE, so P0001 also brings back P00010 */
		for (JsonObject row : askLabBook(filter)) {
			if (code.equals(jsonField(row, "code"))) {
				return toPatient(row);
			}
		}

		throw new ResourceNotFoundException(theId);
	}

	/**
	 * Searches patients by identifier, family name or given name.
	 * @param identifier LabBook code, or laboratory code with its own system.
	 * @param family Family name, partial match.
	 * @param given Given name, partial match.
	 * @return The matching patients.
	 */
	@Search
	public List<Patient> search(
			@OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam identifier,
			@OptionalParam(name = Patient.SP_FAMILY) StringParam family,
			@OptionalParam(name = Patient.SP_GIVEN) StringParam given) {

		JsonObjectBuilder filter = Json.createObjectBuilder();

		if (identifier != null && identifier.getValue() != null) {
			if (SYSTEM_CODE_LAB.equals(identifier.getSystem())) {
				filter.add("code_lab", identifier.getValue());
			} else {
				filter.add("code", identifier.getValue());
			}
		}

		if (family != null && family.getValue() != null) {
			filter.add("lastname", family.getValue());
		}

		if (given != null && given.getValue() != null) {
			filter.add("firstname", given.getValue());
		}

		List<Patient> patients = new ArrayList<>();

		for (JsonObject row : askLabBook(filter.build())) {
			/** The resource id is the patient code, so a patient without one is not addressable */
			if (jsonField(row, "code").isEmpty()) {
				logger.warn("Patient skipped, no LabBook code to use as resource id");
				continue;
			}

			patients.add(toPatient(row));
		}

		return patients;
	}

	/**
	 * Calls patient/list and returns its rows.
	 */
	private List<JsonObject> askLabBook(JsonObject filter) {
		String answer = client.postToLabBook(SERVICE_PATIENT_LIST, filter.toString());

		try (JsonReader reader = Json.createReader(new StringReader(answer))) {
			JsonValue value = reader.readValue();

			/** LabBook answers null when it found nothing */
			if (value.getValueType() != JsonValue.ValueType.ARRAY) {
				return new ArrayList<>();
			}

			return value.asJsonArray().getValuesAs(JsonObject.class);
		}
	}

	/**
	 * Builds the FHIR resource from one LabBook patient row.
	 */
	private Patient toPatient(JsonObject row) {
		Patient patient = new Patient();

		patient.setId(jsonField(row, "code"));

		addIdentifier(patient, SYSTEM_CODE,     jsonField(row, "code"));
		addIdentifier(patient, SYSTEM_CODE_LAB, jsonField(row, "code_lab"));

		String family    = jsonField(row, "lastname");
		String firstname = jsonField(row, "firstname");
		String midname   = jsonField(row, "midname");

		/** An empty name element would be noise, LabBook often has none */
		if (!family.isEmpty() || !firstname.isEmpty() || !midname.isEmpty()) {
			HumanName name = patient.addName().setUse(HumanName.NameUse.OFFICIAL);

			setIfNotEmpty(name::setFamily, family);
			setIfNotEmpty(name::addGiven,  firstname);
			setIfNotEmpty(name::addGiven,  midname);
		}

		String maiden = jsonField(row, "maidenname");

		if (!maiden.isEmpty()) {
			patient.addName().setUse(HumanName.NameUse.MAIDEN).setFamily(maiden);
		}

		patient.setGender(toGender(jsonField(row, "sex")));

		setBirthDate(patient, jsonField(row, "birth"));

		addPhone(patient, jsonField(row, "phone1"));
		addPhone(patient, jsonField(row, "phone2"));

		String email = jsonField(row, "email");

		if (!email.isEmpty()) {
			patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue(email);
		}

		addAddress(patient, row);

		return patient;
	}

	/**
	 * Sets the birth date, and leaves it out when LabBook holds no real date,
	 * the MySQL zero date 0000-00-00 among them.
	 */
	private void setBirthDate(Patient patient, String birth) {
		if (birth.length() < 10) {
			return;
		}

		/** LabBook sends an ISO date, with a time part for a timestamp column */
		String day = birth.substring(0, 10);

		try {
			LocalDate.parse(day);
		} catch (DateTimeParseException e) {
			logger.info("Birth date {} is not a date, left out of the FHIR patient", birth);
			return;
		}

		patient.setBirthDateElement(new DateType(day));
	}

	/**
	 * Adds the postal address when LabBook gave at least one of its parts.
	 */
	private void addAddress(Patient patient, JsonObject row) {
		String line    = jsonField(row, "address");
		String pbox    = jsonField(row, "pbox");
		String suburb  = jsonField(row, "suburb");
		String zipcode = jsonField(row, "zipcode");
		String city    = jsonField(row, "city");

		if (line.isEmpty() && pbox.isEmpty() && suburb.isEmpty() && zipcode.isEmpty() && city.isEmpty()) {
			return;
		}

		Address address = patient.addAddress();

		setIfNotEmpty(address::addLine,      line);
		setIfNotEmpty(address::setDistrict,  suburb);
		setIfNotEmpty(address::setPostalCode, zipcode);
		setIfNotEmpty(address::setCity,      city);

		if (!pbox.isEmpty()) {
			address.setText(pbox);
		}
	}

	/**
	 * Adds one phone number when it is set.
	 */
	private void addPhone(Patient patient, String number) {
		if (!number.isEmpty()) {
			patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(number);
		}
	}

	/**
	 * Adds one identifier when its value is set.
	 */
	private void addIdentifier(Patient patient, String system, String value) {
		if (!value.isEmpty()) {
			patient.addIdentifier().setSystem(system).setValue(value);
		}
	}

	/**
	 * Translates the LabBook sex, already given as M, F or U by the LabBook API.
	 */
	private Enumerations.AdministrativeGender toGender(String sex) {
		switch (sex) {
			case "M":
				return Enumerations.AdministrativeGender.MALE;
			case "F":
				return Enumerations.AdministrativeGender.FEMALE;
			default:
				return Enumerations.AdministrativeGender.UNKNOWN;
		}
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
	 * Calls the setter only when the value is set, to leave empty fields out.
	 */
	private void setIfNotEmpty(Consumer<String> setter, String value) {
		if (!value.isEmpty()) {
			setter.accept(value);
		}
	}
}
