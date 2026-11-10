# Requested analysis - LabBook Connect, FHIR Implementation Guide v0.1.0

* [**Table of Contents**](toc.md)
* [**Artifacts Summary**](artifacts.md)
* **Requested analysis**

## Resource Profile: Requested analysis 

| | |
| :--- | :--- |
| *Official URL*:http://fhir.labbook-connect/ig/StructureDefinition/labbook-analysis-request | *Version*:0.1.0 |
| Draft as of 2026-11-09 | *Computable Name*:LabBookAnalysisRequest |

 
One requested analysis. The laboratory recognises it by its LOINC code, so that code has to be there. 

**Usages:**

* This Profile is not used by any profiles in this Specification

You can also check for [usages in the FHIR IG Statistics](https://packages2.fhir.org/xig/resource/labbook.connect.ig|current/StructureDefinition/StructureDefinition-labbook-analysis-request.json)

### Formal Views of Profile Content

 [Description of Profiles, Differentials, Snapshots and how the different presentations work](http://build.fhir.org/ig/FHIR/ig-guidance/readingIgs.html#structure-definitions). 

 

Other representations of profile: [CSV](StructureDefinition-labbook-analysis-request.csv), [Excel](StructureDefinition-labbook-analysis-request.xlsx), [Schematron](StructureDefinition-labbook-analysis-request.sch) 



## Resource Content

```json
{
  "resourceType" : "StructureDefinition",
  "id" : "labbook-analysis-request",
  "url" : "http://fhir.labbook-connect/ig/StructureDefinition/labbook-analysis-request",
  "version" : "0.1.0",
  "name" : "LabBookAnalysisRequest",
  "title" : "Requested analysis",
  "status" : "draft",
  "date" : "2026-11-09T08:36:26+00:00",
  "publisher" : "Fondation Merieux",
  "description" : "One requested analysis. The laboratory recognises it by its LOINC code, so that code has to be there.",
  "fhirVersion" : "4.0.1",
  "mapping" : [{
    "identity" : "workflow",
    "uri" : "http://hl7.org/fhir/workflow",
    "name" : "Workflow Pattern"
  },
  {
    "identity" : "v2",
    "uri" : "http://hl7.org/v2",
    "name" : "HL7 v2 Mapping"
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
    "identity" : "quick",
    "uri" : "http://siframework.org/cqf",
    "name" : "Quality Improvement and Clinical Knowledge (QUICK)"
  }],
  "kind" : "resource",
  "abstract" : false,
  "type" : "ServiceRequest",
  "baseDefinition" : "http://hl7.org/fhir/StructureDefinition/ServiceRequest",
  "derivation" : "constraint",
  "differential" : {
    "element" : [{
      "id" : "ServiceRequest",
      "path" : "ServiceRequest"
    },
    {
      "id" : "ServiceRequest.status",
      "path" : "ServiceRequest.status",
      "mustSupport" : true
    },
    {
      "id" : "ServiceRequest.priority",
      "path" : "ServiceRequest.priority",
      "short" : "urgent, asap or stat make the analysis urgent in LabBook, anything else is routine",
      "mustSupport" : true
    },
    {
      "id" : "ServiceRequest.code",
      "path" : "ServiceRequest.code",
      "min" : 1,
      "mustSupport" : true
    },
    {
      "id" : "ServiceRequest.code.coding",
      "path" : "ServiceRequest.code.coding",
      "slicing" : {
        "discriminator" : [{
          "type" : "value",
          "path" : "system"
        }],
        "description" : "One coding per terminology the calling system holds",
        "rules" : "open"
      },
      "min" : 1
    },
    {
      "id" : "ServiceRequest.code.coding:loinc",
      "path" : "ServiceRequest.code.coding",
      "sliceName" : "loinc",
      "short" : "LOINC code of the analysis, the one the laboratory matches",
      "min" : 1,
      "max" : "1",
      "mustSupport" : true
    },
    {
      "id" : "ServiceRequest.code.coding:loinc.system",
      "path" : "ServiceRequest.code.coding.system",
      "min" : 1,
      "fixedUri" : "http://loinc.org"
    },
    {
      "id" : "ServiceRequest.code.coding:labbookCode",
      "path" : "ServiceRequest.code.coding",
      "sliceName" : "labbookCode",
      "short" : "LabBook code of the analysis, when the calling system knows it",
      "min" : 0,
      "max" : "1",
      "mustSupport" : true
    },
    {
      "id" : "ServiceRequest.code.coding:labbookCode.system",
      "path" : "ServiceRequest.code.coding.system",
      "min" : 1,
      "fixedUri" : "urn:labbook:analysis-code"
    },
    {
      "id" : "ServiceRequest.subject",
      "path" : "ServiceRequest.subject",
      "mustSupport" : true
    }]
  }
}

```
