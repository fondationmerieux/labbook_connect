# Project

LabBook Connect is a compagnon project to [LabBook](https://www.lab-book.org/en/).

The long term goal of the project is to be able to manage all the communication between LabBook and other healthcare informatics systems.

For now (as of this v1) it focuses on the exchanges with analyzers (In Vitro Diagnostic devices) in the simplest form possible.

A more detailed explanation of the first steps of the project and the choices that were made is available in [this document](doc/first_steps.md).

Technically LabBook Connect v1 is an HTTP proxy to analyzers implementing the IHE-LAW transactions.
It exchanges HL7v2 messages over HTTP with upstream systems like LabBook on one side.
On the other side, it delegates the handling of the HL7v2 messages to plugins, one plugin for each supported type of analyzer.
The plugins are in charge of converting the HL7v2 messages to whatever dialect the analyzer speaks.

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

## HTTP API

You can browse the Java documentation by executing the index.html file available in doc/api/

## Plugin API

The plugin structure must implement the interface described in the Analyzer.java file.
The plugin can use the functions provided in the Connect_util.java file.

# Development environment

The Eclipse 4.29.0 (2023-09) plugin and openjdk 21 were used for this project.
View doc/dependencies.md file

# Test

Installer le conteneur LabBook Connect
Les arborescences suivantes doivent être accessibles : 
/storage/resource/connect/analyzer/plugin/
/storage/resource/connect/analyzer/setting/
Dans plugin copier le fichier AnalyzerDemo.jar
Dans setting copier un fichier de setting suivant cette structure TOML :

version = "1.0"

[analyzer]
brand = ""
name = ""
id = "id_analyzer_demo"  # Must not be empty
plugin= "AnalyzerDemo"   # Do not modify
lab27 = "http://server:8080/connect/test_lab27"
lab28 = "http://server:8080/connect/lab28"
lab29 = "http://server:8080/connect/test_lab29"
mapping = ""

Executer la conteneur avec make devrun

Evoquer le chemin fichier de log !

Structure fichier TOML de test pour lab27 et lab29 :
[message]
  control_id  = "id_of_control"

Pour tester lab27 il faut déposer un fichier TOML dans /storage/resource/connect/analyzer/id_analyzer_demo/lab27 (répertoire créer au lancment de Connect si le fichier de setting et plugin sont bien présent et conforme.
Si le test de lecture du fichier est correct ce dernier se déplacer dans /storage/resource/connect/analyzer/id_analyzer_demo/archive_lab27

Pour tester lab28, vous pouvez executer le curl suivant :
curl -v -X POST "http://server:8080/connect/lab28/id_analyzer_demo" -H "Content-Type: application/hl7-v2" -d "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|202312201130||OML^O33|123456|T|2.5.1"

Pour tester lab29 il faut déposer un fichier TOML dans /storage/resource/connect/analyzer/id_analyzer_demo/lab29 (répertoire créer au lancment de Connect si le fichier de setting et plugin sont bien présent et conforme.
Si le test de lecture du fichier est correct ce dernier se déplacer dans /storage/resource/connect/analyzer/id_analyzer_demo/archive_lab29

# Changes

You can have a look at [CHANGELOG.md](CHANGELOG.md) for changes to the program.

# Contributing

We happily accept contributions but we opened this repository only very recently so we have a long way to go to make contributing easy.

Feel free to open issues when things are confused.

# Licence

[GNU General Public License v2.0](LICENSE.md)
