# Building the Implementation Guide

The guide is written in FSH, FHIR Shorthand, under `input/fsh/`. Two tools turn it into a
website: **SUSHI**, which compiles FSH into FHIR resources, and the **IG Publisher**, which
turns those resources into HTML pages.

Both run in containers, so nothing has to be installed on the workstation and every build
uses the same versions.

## Once, before the first build

```
podman pull docker.io/library/node:20
podman pull docker.io/hl7fhir/ig-publisher-base:latest
curl -L -o ~/bin/publisher.jar \
     https://github.com/HL7/fhir-ig-publisher/releases/latest/download/publisher.jar
```

The jar is 237 MB and the publisher fills `~/.fhir` with the FHIR definitions on its first
run, around 1 GB. Both are caches, outside the repository.

## Building

```
make sushi     # FSH -> FHIR resources, in fsh-generated/
make publish   # FHIR resources -> website, in output/
make clean     # removes everything the two steps produce
```

Then open `output/index.html`. The quality report of the build is `output/qa.html`, and a
build is good when it says 0 errors.

## What is versioned

Only the sources: `sushi-config.yaml`, `ig.ini`, `Makefile`, `input/` and this file.
`fsh-generated/`, `output/`, `temp/`, `template/` and `input-cache/` are produced by the two
steps and left out by `.gitignore`.

## Traps already met

- **The publisher runs SUSHI itself** when it finds FSH sources, and waits five minutes
  before giving up when it cannot. The `sushi` target already did that work, hence the
  `-no-sushi` of the `publish` target.
- **`ig.ini` needs a `template` line**, otherwise SUSHI stops on `The ig.ini file must have a
  "template" property`.
- **Every page carries a banner** ending with a link to the directory of published versions of
  the guide. Nothing is published, so that link leads nowhere: the `trim` target takes the
  sentence out once the pages are built, and keeps the banner itself.
- **The canonical is an identifier, not an address.** `http://fhir.labbook-connect/ig` serves
  nothing, which the publisher complains about. Those complaints are listed in
  `input/ignoreWarnings.txt`, with the reason.
- **A display name on a LOINC code is checked** against the official one. Leaving the display
  out is simpler, and it is what the calling system sends anyway.
