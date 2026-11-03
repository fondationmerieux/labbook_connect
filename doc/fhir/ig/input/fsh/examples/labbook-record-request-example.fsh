Instance:    RecordRequestPatient
InstanceOf:  Patient
Usage:       #inline
* identifier[0].system = "http://example.org/openmrs"
* identifier[0].value = "100000Y"
* name[0].family = "WILLIAMS"
* name[0].given[0] = "Betty"
* gender = #female
* birthDate = "1974-03-15"

Instance:    RecordRequestPractitioner
InstanceOf:  Practitioner
Usage:       #inline
* name[0].family = "DOCTOR"
* name[0].given[0] = "Jake"

Instance:    RecordRequestAnalysis
InstanceOf:  LabBookAnalysisRequest
Usage:       #inline
* status = #active
* intent = #order
* code.coding[loinc].system = "http://loinc.org"
* code.coding[loinc].code = #24331-1
* code.text = "Lipid panel"
* subject.reference = "urn:uuid:3a1b8b1e-0e6a-4f42-9e0b-1c2d3e4f5a61"
* authoredOn = "2026-10-27T13:51:03+00:00"

Instance:    RecordRequestTask
InstanceOf:  LabBookRecordTask
Usage:       #inline
* identifier[0].system = "http://example.org/openmrs"
* identifier[0].value = "c26cf3f3-e90f-4237-aa82-df73aaf5a69d"
* status = #requested
* intent = #order
* for.reference = "urn:uuid:3a1b8b1e-0e6a-4f42-9e0b-1c2d3e4f5a61"
* basedOn[0].reference = "urn:uuid:5c3d0f30-2a8c-4b64-af2d-3e4f5a6b7c83"
* authoredOn = "2026-10-27T13:51:03+00:00"

Instance:    RecordRequestExample
InstanceOf:  Bundle
Usage:       #example
Title:       "Record request, the whole message"
Description: "The single call an outside system makes to create a record. Names, dates and references are made up."

* type = #transaction

* entry[0].fullUrl = "urn:uuid:7e4f1a20-4b9d-4c75-b03e-4f5a6b7c8d94"
* entry[0].resource = RecordRequestTask
* entry[0].request.method = #POST
* entry[0].request.url = "Task"

* entry[1].fullUrl = "urn:uuid:5c3d0f30-2a8c-4b64-af2d-3e4f5a6b7c83"
* entry[1].resource = RecordRequestAnalysis
* entry[1].request.method = #POST
* entry[1].request.url = "ServiceRequest"

* entry[2].fullUrl = "urn:uuid:3a1b8b1e-0e6a-4f42-9e0b-1c2d3e4f5a61"
* entry[2].resource = RecordRequestPatient
* entry[2].request.method = #POST
* entry[2].request.url = "Patient"

* entry[3].fullUrl = "urn:uuid:9f5a2b31-6c0e-4d86-c14f-5a6b7c8d9ea5"
* entry[3].resource = RecordRequestPractitioner
* entry[3].request.method = #POST
* entry[3].request.url = "Practitioner"
