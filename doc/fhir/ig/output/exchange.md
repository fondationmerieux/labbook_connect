# The exchange - LabBook Connect, FHIR Implementation Guide v0.1.0

* [**Table of Contents**](toc.md)
* **The exchange**

## The exchange

This page describes one exchange, the creation of a record from an outside request. The profiles say what each resource holds, this page says what travels and what comes back.

### What the caller sends

One transaction Bundle, in a single call.

```
POST http://<address of LabBook Connect>/fhir
Content-Type: application/fhir+json

```

The Bundle holds four resources, the Task carrying the request, one ServiceRequest per requested analysis, the Patient, and the Practitioner who prescribed. A ServiceRequest whose status is not `active` is left aside, so a request where every analysis is cancelled creates no record.

### What comes back when the record is created

A `transaction-response` Bundle.

```
{
  "resourceType": "Bundle",
  "type": "transaction-response",
  "entry": [
    {
      "response": {
        "status": "201 Created",
        "location": "Task/312"
      }
    }
  ]
}

```

`location` carries the identifier LabBook gave the record, `312` here. Two things to know about it. It is the internal identifier, not the record number the laboratory reads on screen, which LabBook builds on its own side. And the facade holds no service yet to read a record back, so this identifier is of no use before that service exists.

The handle the caller should rely on is its own reference, the one it sent in `Task.identifier`. LabBook keeps it next to the record, and refuses a second request carrying the same one.

### What comes back when the request is refused

The message always arrives in an OperationOutcome, with one of these HTTP statuses.

| | | |
| :--- | :--- | :--- |
| 400 | The Bundle holds no Patient, LabBook cannot create a record without one | no Patient in the Bundle |
| 400 | The Bundle holds no ServiceRequest, a record needs at least one analysis | no analysis in the Bundle |
| 400 | Every ServiceRequest of the Bundle is cancelled or closed, no record is created | every analysis is cancelled |
| 400 | LabBook does not know this analysis | the LOINC code matches no analysis of the laboratory |
| 400 | LabBook refused the request | LabBook refused for another reason, which the message carries |
| 409 | This request has already been received by LabBook | a request carrying the same reference was already received |
| 500 | LabBook answered HTTP … | LabBook or its network failed, nothing to correct on the caller side |

Nothing is created on a refusal, the whole transaction is dropped.

