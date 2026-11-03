Profile:     LabBookAnalysisRequest
Parent:      ServiceRequest
Id:          labbook-analysis-request
Title:       "Requested analysis"
Description: "One requested analysis. The laboratory recognises it by its LOINC code, so that code has to be there."

// Only a living request becomes an analysis, any other status is left aside
* status 1..1 MS
* intent 1..1

* code 1..1 MS
* code.coding ^slicing.discriminator.type = #value
* code.coding ^slicing.discriminator.path = "system"
* code.coding ^slicing.rules = #open
* code.coding ^slicing.description = "One coding per terminology the calling system holds"
* code.coding contains
    loinc 1..1 MS and
    labbookCode 0..1 MS
* code.coding[loinc].system = "http://loinc.org" (exactly)
* code.coding[loinc] ^short = "LOINC code of the analysis, the one the laboratory matches"
* code.coding[labbookCode].system = "urn:labbook:analysis-code" (exactly)
* code.coding[labbookCode] ^short = "LabBook code of the analysis, when the calling system knows it"

* subject 1..1 MS
* priority MS
* priority ^short = "urgent, asap or stat make the analysis urgent in LabBook, anything else is routine"
