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

TODO

## Plugin API

TODO

# Development environment

TODO

# Changes

You can have a look at [CHANGELOG.md](CHANGELOG.md) for changes to the program.

# Contributing

We happily accept contributions but we opened this repository only very recently so we have a long way to go to make contributing easy.

Feel free to open issues when things are confused.

# Licence

[GNU General Public License v2.0](LICENSE.md)
