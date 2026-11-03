This guide describes the FHIR exchange between an outside system, such as OpenMRS, and the
LabBook laboratory information system.

LabBook itself does not speak FHIR. The exchange goes through the FHIR facade of LabBook
Connect, which translates every call into the LabBook external API and translates the answer
back.

### What the facade does today

| Exchange | FHIR | State |
|---|---|---|
| Read a patient, search patients | `GET Patient` | in service |
| Create a record from a request | `POST` of a transaction Bundle | in service |
| Send the results back | `DiagnosticReport`, `Observation` | to come |

### Where the field by field mapping lives

The mapping of every LabBook field to its FHIR element is kept beside this guide, in
`doc/fhir/mapping.md` of the LabBook Connect repository. This guide carries the profiles, that
file carries the whole picture.
