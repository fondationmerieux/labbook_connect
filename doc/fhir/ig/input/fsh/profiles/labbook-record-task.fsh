Profile:     LabBookRecordTask
Parent:      Task
Id:          labbook-record-task
Title:       "Record request"
Description: "The work order an outside system sends to ask LabBook for a new record. It carries the reference LabBook keeps to recognise the request, and points to the requested analyses."

* status 1..1 MS
* intent = #order (exactly)

// LabBook keeps this reference and refuses a second request carrying the same one. Without
// an identifier, the resource id is taken as the reference.
* identifier 0..* MS
* identifier ^short = "Reference of the request in the calling system"

* for 1..1 MS
* for ^short = "The patient of the record, also sent as a Patient resource of the Bundle"

* basedOn MS
* basedOn ^short = "The requested analyses, each one a ServiceRequest of the Bundle"

* authoredOn MS
* authoredOn ^short = "Date of prescription of the record"

* note MS
* note ^short = "Free comment, kept on the LabBook record"
