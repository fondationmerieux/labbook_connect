# Project

LabBook Connect is a compagnon project to [LabBook](https://www.lab-book.org/en/).

The long term goal of the project is to be able to manage all the communication between LabBook and other healthcare informatics systems.

For now (as of this v1) it focuses on the exchanges with analyzers (In Vitro Diagnostic devices) in the simplest form possible.

A more detailed explanation of the first steps of the project and the choices that were made is available in [this document](doc/first_steps.md).

Technically LabBook Connect v1 is an HTTP proxy to analyzers implementing the IHE-LAW transactions.
It exchanges HL7v2 messages over HTTP with upstream systems like LabBook on one side.
On the other side, it delegates the handling of the HL7v2 messages to plugins, one plugin for each supported type of analyzer.
The plugins are in charge of converting the HL7v2 messages to whatever dialect the analyzer speaks.
Each plugin comes in the form of a java archive .jar file.
On startup, LabBook Connect searchs for available plugins and runs them.

For now, LabBook Connect doesn't implement any mapping.
The codes in the HL7v2 messages must be those expected by the targeted analyzer.

LabBook Connect is developped in java.

This repository contains the material needed to build the LabBook Connect container image.

Each analyzer plugin is available in its own repository.

For convenience, this repository contains a prebuilt version of LabBook Connect in `bin/labbook_connect.jar` and
a plugin (AnalyzerDemo) in binary form in `resource/connect/analyzer/plugin/AnalyzerDemo.jar`.
This allows you to build a container without installing the development environment and building a plugin.

For more information about plugins in general and the AnalyzerDemo plugin in particular please refer to the [AnalyzerDemo plugin repository](URL).

# Requirements

- linux
- podman
- make
- git

For development:

- java
- eclipse

# Installation and usage

## Clone the repository

~~~
git clone https://github.com/fondationmerieux/labbook_connect.git
~~~

## Build and run the container

~~~
$ make help
Usage:

  make devbuild      build image localhost/labbook-connect:latest from working directory
  make devclean      remove image localhost/labbook-connect:latest
  make devrun        run the application access from http://localhost:8080/connect
  make devstop       stop the application
  make devreload     stop, clean, build and run
~~~

## Source documentation

You can browse the Java documentation by pointing to the `doc/api/index.html` file.

## HTTP API

LabBook Connect uses the
[HAPI HL7 over HTTP specification](https://hapifhir.github.io/hapi-hl7v2/hapi-hl7overhttp/specification.html)
to transport HL7v2 messages over HTTP.

LabBook Connect uses HTTP to implement the 3 IHE-LAW transactions:

### [LAB-27]

LabBook Connect is the client, sends POST messages to an upstrean URL,

![LAB-27 diagram image](doc/lab27.png)

[LAB-27 diagram source code](doc/lab27.puml).

### [LAB-28]

LabBook Connect is the server, receives POST messages from upstrean,

![LAB-28 diagram image](doc/lab28.png)

[LAB-28 diagram source code](doc/lab28.puml).

### [LAB-29]

LabBook Connect is the client, sends POST messages to an upstrean URL,

![LAB-29 diagram image](doc/lab29.png)

[LAB-29 diagram source code](doc/lab29.puml).

## Plugin API

The plugin must implement the interface described in the `src/main/java/plugin/Analyzer.java` file.
Please see the comments in the file for details.

The plugin can use the functions provided in the `src/main/java/plugin/Connect_util.java` file to communicate with LabBook Connect.

# Development environment

The Eclipse 4.29.0 (2023-09) plugin and openjdk 21 were used for this project.
Please see `doc/dependencies.md`.

# Configuration

LabBook Connect runs in a container.
It expects a permanent volume to be accessible at `/storage/`.
It writes logs into `/app/logs/`.

On startup LabBook Connect:

- loads the plugins present in `/storage/resource/connect/analyzer/plugin/`,
- reads the configuration files present in `/storage/resource/connect/analyzer/setting`,
- creates a directory for each analyzer present in the configuration files.
  The directory name is the plugin ID.

A configuration file example with a demo analyzer in available in `storage/resource/connect/analyzer/setting/id_analyzer_demo.toml`.

# Test with the AnalyzerDemo plugin

The project contains a demo plugin with a basic configuration:

- AnalyzerDemo plugin in `resource/connect/analyzer/plugin/AnalyzerDemo.jar`,
- configuration file in `/storage/resource/connect/analyzer/setting/id_analyzer_demo.toml`,

Start the container:

`$ make devrun`

Test it started:

~~~
$ curl http://localhost:8080/connect/test
1.0.0
~~~

The container is started with volume maps for /storage and /app/logs.

LabBook Connect uses the /storage volume to hold various files.
The initial content of the volume is stored in the `./storage` directory of the source tree.
In order to prevent modification of this directory it is replicated to a `DEVRUN_STORAGE` directory before mounting it into the container.
`DEVRUN_STORAGE=./devrun_storage` by default, you can modify it by setting the `DEVRUN_STORAGE` environment variable.

Similarly the directory for logs is defined by default `DEVRUN_LOG_DIR=./logs`
You can modify it by setting the `DEVRUN_LOG_DIR` environment variable.

## Test LAB-27

The AnalyzerDemo plugin reads incoming queries in `/storage/resource/connect/analyzer/<ID>/lab27/`.
The files must be in the TOML format and contain:

~~~
[message]
  control_id  = "id_of_control"
~~~

An HTTP POST request is sent to the upstream lab27 endpoint defined in the configuration with a dummy HL7 OBP_Q11 message payload.

After that the file is moved to `/storage/resource/connect/analyzer/<ID>/archive_lab27/`.

## Test LAB-28

You can send test OML_33 messages to the lab28 endpoint:

~~~
curl -v -X POST "http://server:8080/connect/lab28/id_analyzer_demo"\
    -H "Content-Type: application/hl7-v2"\
    -d "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|202312201130||OML^O33|123456|T|2.5.1"
~~~

## Test LAB-29

The AnalyzerDemo plugin reads incoming status changes in `/storage/resource/connect/analyzer/<ID>/lab29/`.
The files must be in the TOML format and contain:

~~~
[message]
  control_id  = "id_of_control"
~~~

An HTTP POST request is sent to the upstream lab29 endpoint defined in the configuration with a dummy HL7 OUL_R22 message payload.

After that the file is moved to `/storage/resource/connect/analyzer/<ID>/archive_lab29/`.

# Changes

You can have a look at [CHANGELOG.md](CHANGELOG.md) for changes to the program.

# Contributing

We happily accept contributions but we opened this repository only very recently so we have a long way to go to make contributing easy.

Feel free to open issues when things are confused.

# Licence

[GNU General Public License v2.0](LICENSE.md)
