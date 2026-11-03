# Record request - LabBook Connect, FHIR Implementation Guide v0.1.0

* [**Table of Contents**](toc.md)
* [**Artifacts Summary**](artifacts.md)
* **Record request**

## Resource Profile: Record request 

| | |
| :--- | :--- |
| *Official URL*:http://fhir.labbook-connect/ig/StructureDefinition/labbook-record-task | *Version*:0.1.0 |
| Draft as of 2026-10-28 | *Computable Name*:LabBookRecordTask |

 
The work order an outside system sends to ask LabBook for a new record. It carries the reference LabBook keeps to recognise the request, and points to the requested analyses. 

**Usages:**

* This Profile is not used by any profiles in this Specification

You can also check for [usages in the FHIR IG Statistics](https://packages2.fhir.org/xig/resource/labbook.connect.ig|current/StructureDefinition/StructureDefinition-labbook-record-task.json)

### Formal Views of Profile Content

 [Description of Profiles, Differentials, Snapshots and how the different presentations work](http://build.fhir.org/ig/FHIR/ig-guidance/readingIgs.html#structure-definitions). 

 

Other representations of profile: [CSV](StructureDefinition-labbook-record-task.csv), [Excel](StructureDefinition-labbook-record-task.xlsx), [Schematron](StructureDefinition-labbook-record-task.sch) 



## Resource Content

```json
{
  "resourceType" : "StructureDefinition",
  "id" : "labbook-record-task",
  "url" : "http://fhir.labbook-connect/ig/StructureDefinition/labbook-record-task",
  "version" : "0.1.0",
  "name" : "LabBookRecordTask",
  "title" : "Record request",
  "status" : "draft",
  "date" : "2026-10-28T08:35:15+00:00",
  "publisher" : "Fondation Merieux",
  "description" : "The work order an outside system sends to ask LabBook for a new record. It carries the reference LabBook keeps to recognise the request, and points to the requested analyses.",
  "fhirVersion" : "4.0.1",
  "mapping" : [{
    "identity" : "workflow",
    "uri" : "http://hl7.org/fhir/workflow",
    "name" : "Workflow Pattern"
  },
  {
    "identity" : "rim",
    "uri" : "http://hl7.org/v3",
    "name" : "RIM Mapping"
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
  }],
  "kind" : "resource",
  "abstract" : false,
  "type" : "Task",
  "baseDefinition" : "http://hl7.org/fhir/StructureDefinition/Task",
  "derivation" : "constraint",
  "differential" : {
    "element" : [{
      "id" : "Task",
      "path" : "Task"
    },
    {
      "id" : "Task.identifier",
      "path" : "Task.identifier",
      "short" : "Reference of the request in the calling system",
      "mustSupport" : true
    },
    {
      "id" : "Task.basedOn",
      "path" : "Task.basedOn",
      "short" : "The requested analyses, each one a ServiceRequest of the Bundle",
      "mustSupport" : true
    },
    {
      "id" : "Task.status",
      "path" : "Task.status",
      "mustSupport" : true
    },
    {
      "id" : "Task.intent",
      "path" : "Task.intent",
      "fixedCode" : "order"
    },
    {
      "id" : "Task.for",
      "path" : "Task.for",
      "short" : "The patient of the record, also sent as a Patient resource of the Bundle",
      "min" : 1,
      "mustSupport" : true
    },
    {
      "id" : "Task.authoredOn",
      "path" : "Task.authoredOn",
      "short" : "Date of prescription of the record",
      "mustSupport" : true
    },
    {
      "id" : "Task.note",
      "path" : "Task.note",
      "short" : "Free comment, kept on the LabBook record",
      "mustSupport" : true
    }]
  }
}

```
