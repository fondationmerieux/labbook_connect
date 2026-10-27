# LabBook to FHIR R4 mapping

## Overview

| LabBook service | Part | FHIR resource |
|---|---|---|
| `POST record/det` | `external` | Task, ServiceRequest and Patient (their `identifier`) |
| | `patient` | Patient |
| | `prescriber` | Practitioner (and PractitionerRole for the specialty) |
| | `record` | Task and Encounter |
| | `ana_list` | ServiceRequest, one per analysis |
| | `samp_list` | Specimen |
| `POST record/list/<id_pres>` | `stat_rec` | `Task.status` |
| | `num_rec` | `Task.identifier` |
| `GET result/<id_rec>` | `analysis` | DiagnosticReport |
| | `variables` | Observation |
| `POST patient/list` | | Patient |

## Patient (`patient`)

| LabBook | FHIR | Note |
|---|---|---|
| `pat_code` | `Patient.identifier` | LabBook `system`, unique code |
| `pat_code_lab` | `Patient.identifier` | laboratory `system` |
| `pat_name` | `Patient.name.family` | |
| `pat_firstname`, `pat_middle_name` | `Patient.name.given` | |
| `pat_maiden_name` | `Patient.name` | `use` = maiden |
| `pat_sex` | `Patient.gender` | M / F / U to male / female / unknown |
| `pat_birth` | `Patient.birthDate` | |
| `pat_age`, `pat_age_unit` | no direct equivalent | |
| `pat_date_approx` | no direct equivalent | |
| `pat_anonymous` | no direct equivalent | |
| `pat_address`, `pat_pbox`, `pat_area`, `pat_zipcode`, `pat_city` | `Patient.address` | |
| `pat_phone1`, `pat_phone2` | `Patient.telecom` | `system` = phone |
| `pat_nationality` | no direct equivalent | |
| `pat_job`, `pat_resident` | no direct equivalent | |
| `pat_blood_group`, `pat_blood_rhesus` | no direct equivalent | |

## Prescriber (`prescriber`)

| LabBook | FHIR | Note |
|---|---|---|
| `prescr_code` | `Practitioner.identifier` | |
| `prescr_name` | `Practitioner.name.family` | |
| `prescr_firstname` | `Practitioner.name.given` | |
| `prescr_title` | `Practitioner.name.prefix` | |
| `prescr_initial` | no direct equivalent | |
| `prescr_spe` | `PractitionerRole.specialty` | LabBook codes with their own `system`, OpenMRS does not handle this data |
| `prescr_workplace` | `PractitionerRole.organization` | |
| `prescr_service` | `PractitionerRole.location` | |
| `prescr_address`, `prescr_zipcode`, `prescr_city` | `Practitioner.address` | |
| `prescr_phone`, `prescr_mobile`, `prescr_fax`, `prescr_email` | `Practitioner.telecom` | |

## Record (`record`)

| LabBook | FHIR | Note |
|---|---|---|
| `rec_date_prescr` | `ServiceRequest.authoredOn`, `Task.authoredOn` | |
| `rec_date` | `Task.executionPeriod.start` | date received by the laboratory |
| `rec_type` | no direct equivalent | E or I, always E for an outside request |
| `rec_num_int` | no direct equivalent | |
| `rec_comm` | `Task.note` | |
| `rec_int_service` | `Encounter.location` or `ServiceRequest.requester` | requesting ward |
| `rec_bed_num` | `Encounter.location` | |
| `rec_hosp_num` | `Encounter.identifier` | |
| `rec_date_hosp` | `Encounter.period.start` | |
| `rec_custody`, `rec_parcel_*` | no direct equivalent | |
| billing (`rec_invoice_discount`, `rec_percent_discount`, `rec_insurance_cover`, `rec_receipt_num`) | no direct equivalent | out of scope |

## Analyses (`ana_list`)

| LabBook | FHIR | Note |
|---|---|---|
| `ana_loinc` | `ServiceRequest.code` | `system` = `http://loinc.org`, code used first |
| `ana_code` | `ServiceRequest.code` | LabBook `system`, when there is no LOINC |
| `ana_emer` | `ServiceRequest.priority` | Y / N to urgent / routine |
| `ana_req` | no direct equivalent | |
| `ana_out` | `ServiceRequest.performer` | analysis sent out |

## Samples (`samp_list`)

| LabBook | FHIR | Note |
|---|---|---|
| `samp_ana` | `ServiceRequest.specimen` | analysis to sample link |
| `samp_type` | `Specimen.type` | LabBook list (BLD, URN, CSF and so on) still to be mapped |
| `samp_code` | `Specimen.identifier` | |
| `samp_date` | `Specimen.collection.collectedDateTime` | |
| `samp_date_receipt` | `Specimen.receivedTime` | |
| `samp_name` | `Specimen.collection.collector` | |
| `samp_status` | `Specimen.status` | D / T / P, mapping still to be settled |
| `samp_comm` | `Specimen.note` | |

## Record status (`record/list`, `stat_rec` field)

| LabBook | `Task.status` | `DiagnosticReport.status` |
|---|---|---|
| N, new | `accepted` | none, the report does not exist yet |
| A, IA, administrative validation | `in-progress` | `registered` |
| T, IT, technical validation | `in-progress` | `preliminary` |
| B, biological validation | `completed` | `final` |

## Results (`result`)

| LabBook | FHIR | Note |
|---|---|---|
| analysis (`ref_ana`, `name`) | `DiagnosticReport.code` | has to be LOINC for OpenMRS |
| `variables[]` | `DiagnosticReport.result` to the Observation | |
| `var_loinc` | `Observation.code` | `system` = `http://loinc.org`, code used first |
| `code_var`, `label` | `Observation.code` | LabBook `system`, when there is no LOINC |
| `value` and `unit`, numeric | `Observation.valueQuantity` | |
| `value`, list of choices (dictionary) | `Observation.valueCodeableConcept` or `valueString` | |

## Outside system references (`external`)

| LabBook | FHIR | Note |
|---|---|---|
| `system` | `identifier.system` of Task, ServiceRequest and Patient | name of the calling system |
| `record` | `Task.identifier`, `ServiceRequest.identifier` | refused when already registered |
| `patient` | `Patient.identifier` | |

## Sources

- [FHIR R4 resource list](https://hl7.org/fhir/R4/resourcelist.html), one page per resource
  with its attributes, their type and their cardinality
- [OpenMRS FHIR implementation guide](https://fhir.openmrs.org), the resources its FHIR2
  module can read and write
