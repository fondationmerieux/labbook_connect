Instance:    LabBookPatientExample
InstanceOf:  LabBookPatient
Usage:       #example
Title:       "Patient, as the facade publishes it"
Description: "A patient of LabBook read through the facade. Names and dates are made up."

* identifier[labbookCode].system = "urn:labbook:patient-code"
* identifier[labbookCode].value = "FBCFU"
* name[0].use = #official
* name[0].family = "TEST"
* name[0].given[0] = "Florient"
* gender = #male
* birthDate = "1996-07-22"
