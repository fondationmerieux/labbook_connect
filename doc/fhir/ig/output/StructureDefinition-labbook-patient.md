# LabBook patient - LabBook Connect, FHIR Implementation Guide v0.1.0

* [**Table of Contents**](toc.md)
* [**Artifacts Summary**](artifacts.md)
* **LabBook patient**

## Resource Profile: LabBook patient 

| | |
| :--- | :--- |
| *Official URL*:http://fhir.labbook-connect/ig/StructureDefinition/labbook-patient | *Version*:0.1.0 |
| Draft as of 2026-11-09 | *Computable Name*:LabBookPatient |

 
Patient as the FHIR facade of LabBook Connect publishes it, built from the LabBook external API. 

**Usages:**

* Examples for this Profile: [Patient/LabBookPatientExample](Patient-LabBookPatientExample.md)

You can also check for [usages in the FHIR IG Statistics](https://packages2.fhir.org/xig/resource/labbook.connect.ig|current/StructureDefinition/StructureDefinition-labbook-patient.json)

### Formal Views of Profile Content

 [Description of Profiles, Differentials, Snapshots and how the different presentations work](http://build.fhir.org/ig/FHIR/ig-guidance/readingIgs.html#structure-definitions). 

 

Other representations of profile: [CSV](StructureDefinition-labbook-patient.csv), [Excel](StructureDefinition-labbook-patient.xlsx), [Schematron](StructureDefinition-labbook-patient.sch) 



## Resource Content

```json
{
  "resourceType" : "StructureDefinition",
  "id" : "labbook-patient",
  "url" : "http://fhir.labbook-connect/ig/StructureDefinition/labbook-patient",
  "version" : "0.1.0",
  "name" : "LabBookPatient",
  "title" : "LabBook patient",
  "status" : "draft",
  "date" : "2026-11-09T08:36:26+00:00",
  "publisher" : "Fondation Merieux",
  "description" : "Patient as the FHIR facade of LabBook Connect publishes it, built from the LabBook external API.",
  "fhirVersion" : "4.0.1",
  "mapping" : [{
    "identity" : "rim",
    "uri" : "http://hl7.org/v3",
    "name" : "RIM Mapping"
  },
  {
    "identity" : "cda",
    "uri" : "http://hl7.org/v3/cda",
    "name" : "CDA (R2)"
  },
  {
    "identity" : "w5",
    "uri" : "http://hl7.org/fhir/fivews",
    "name" : "FiveWs Pattern Mapping"
  },
  {
    "identity" : "v2",
    "uri" : "http://hl7.org/v2",
    "name" : "HL7 v2 Mapping"
  },
  {
    "identity" : "loinc",
    "uri" : "http://loinc.org",
    "name" : "LOINC code for the element"
  }],
  "kind" : "resource",
  "abstract" : false,
  "type" : "Patient",
  "baseDefinition" : "http://hl7.org/fhir/StructureDefinition/Patient",
  "derivation" : "constraint",
  "differential" : {
    "element" : [{
      "id" : "Patient",
      "path" : "Patient"
    },
    {
      "id" : "Patient.identifier",
      "path" : "Patient.identifier",
      "slicing" : {
        "discriminator" : [{
          "type" : "value",
          "path" : "system"
        }],
        "description" : "One identifier per coding system LabBook holds",
        "rules" : "open"
      },
      "min" : 1,
      "mustSupport" : true
    },
    {
      "id" : "Patient.identifier:labbookCode",
      "path" : "Patient.identifier",
      "sliceName" : "labbookCode",
      "short" : "Unique code given by LabBook",
      "min" : 1,
      "max" : "1",
      "mustSupport" : true
    },
    {
      "id" : "Patient.identifier:labbookCode.system",
      "path" : "Patient.identifier.system",
      "min" : 1,
      "fixedUri" : "urn:labbook:patient-code"
    },
    {
      "id" : "Patient.identifier:labbookCode.value",
      "path" : "Patient.identifier.value",
      "min" : 1
    },
    {
      "id" : "Patient.identifier:laboratoryCode",
      "path" : "Patient.identifier",
      "sliceName" : "laboratoryCode",
      "short" : "Code given by the laboratory, free text, not unique",
      "min" : 0,
      "max" : "1",
      "mustSupport" : true
    },
    {
      "id" : "Patient.identifier:laboratoryCode.system",
      "path" : "Patient.identifier.system",
      "min" : 1,
      "fixedUri" : "urn:labbook:patient-code-lab"
    },
    {
      "id" : "Patient.name",
      "path" : "Patient.name",
      "short" : "Official name, and maiden name when LabBook holds one",
      "mustSupport" : true
    },
    {
      "id" : "Patient.telecom",
      "path" : "Patient.telecom",
      "mustSupport" : true
    },
    {
      "id" : "Patient.gender",
      "path" : "Patient.gender",
      "mustSupport" : true
    },
    {
      "id" : "Patient.birthDate",
      "path" : "Patient.birthDate",
      "mustSupport" : true
    },
    {
      "id" : "Patient.address",
      "path" : "Patient.address",
      "mustSupport" : true
    },
    {
      "id" : "Patient.contact",
      "path" : "Patient.contact",
      "max" : "0"
    },
    {
      "id" : "Patient.communication",
      "path" : "Patient.communication",
      "max" : "0"
    },
    {
      "id" : "Patient.generalPractitioner",
      "path" : "Patient.generalPractitioner",
      "max" : "0"
    },
    {
      "id" : "Patient.managingOrganization",
      "path" : "Patient.managingOrganization",
      "max" : "0"
    },
    {
      "id" : "Patient.link",
      "path" : "Patient.link",
      "max" : "0"
    }]
  }
}

```
