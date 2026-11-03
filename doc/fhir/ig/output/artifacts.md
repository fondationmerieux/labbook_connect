# Artifacts Summary - LabBook Connect, FHIR Implementation Guide v0.1.0

* [**Table of Contents**](toc.md)
* **Artifacts Summary**

## Artifacts Summary

This page provides a list of the FHIR artifacts defined as part of this implementation guide.

### Structures: Resource Profiles 

These define constraints on FHIR resources for systems conforming to this implementation guide.

| | |
| :--- | :--- |
| [LabBook patient](StructureDefinition-labbook-patient.md) | Patient as the FHIR facade of LabBook Connect publishes it, built from the LabBook external API. |
| [Record request](StructureDefinition-labbook-record-task.md) | The work order an outside system sends to ask LabBook for a new record. It carries the reference LabBook keeps to recognise the request, and points to the requested analyses. |
| [Requested analysis](StructureDefinition-labbook-analysis-request.md) | One requested analysis. The laboratory recognises it by its LOINC code, so that code has to be there. |

### Example: Example Instances 

These are example instances that show what data produced and consumed by systems conforming with this implementation guide might look like.

| | |
| :--- | :--- |
| [Patient, as the facade publishes it](Patient-LabBookPatientExample.md) | A patient of LabBook read through the facade. Names and dates are made up. |
| [Record request, the whole message](Bundle-RecordRequestExample.md) | The single call an outside system makes to create a record. Names, dates and references are made up. |

