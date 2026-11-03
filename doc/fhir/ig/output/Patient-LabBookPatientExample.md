# Patient, as the facade publishes it - LabBook Connect, FHIR Implementation Guide v0.1.0

* [**Table of Contents**](toc.md)
* [**Artifacts Summary**](artifacts.md)
* **Patient, as the facade publishes it**

## Example Patient: Patient, as the facade publishes it

Profile: [LabBook patient](StructureDefinition-labbook-patient.md)

Florient TEST (official) Male, DoB: 1996-07-22 ( urn:labbook:patient-code#FBCFU)

-------



## Resource Content

```json
{
  "resourceType" : "Patient",
  "id" : "LabBookPatientExample",
  "meta" : {
    "profile" : ["http://fhir.labbook-connect/ig/StructureDefinition/labbook-patient"]
  },
  "identifier" : [{
    "system" : "urn:labbook:patient-code",
    "value" : "FBCFU"
  }],
  "name" : [{
    "use" : "official",
    "family" : "TEST",
    "given" : ["Florient"]
  }],
  "gender" : "male",
  "birthDate" : "1996-07-22"
}

```
