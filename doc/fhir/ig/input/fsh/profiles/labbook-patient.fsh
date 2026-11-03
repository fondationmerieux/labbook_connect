Profile:     LabBookPatient
Parent:      Patient
Id:          labbook-patient
Title:       "LabBook patient"
Description: "Patient as the FHIR facade of LabBook Connect publishes it, built from the LabBook external API."

// The resource id is the LabBook patient code, so a patient without one is not addressable
* identifier 1..* MS
* identifier ^slicing.discriminator.type = #value
* identifier ^slicing.discriminator.path = "system"
* identifier ^slicing.rules = #open
* identifier ^slicing.description = "One identifier per coding system LabBook holds"
* identifier contains
    labbookCode 1..1 MS and
    laboratoryCode 0..1 MS
* identifier[labbookCode].system = "urn:labbook:patient-code" (exactly)
* identifier[labbookCode].value 1..1
* identifier[labbookCode] ^short = "Unique code given by LabBook"
* identifier[laboratoryCode].system = "urn:labbook:patient-code-lab" (exactly)
* identifier[laboratoryCode] ^short = "Code given by the laboratory, free text, not unique"

* name MS
* name ^short = "Official name, and maiden name when LabBook holds one"
* gender MS
* birthDate MS
* address MS
* telecom MS

// LabBook holds no equivalent, so the facade never sends these
* contact 0..0
* communication 0..0
* generalPractitioner 0..0
* managingOrganization 0..0
* link 0..0
