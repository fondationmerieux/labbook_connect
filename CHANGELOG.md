# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.3] - 2025-03-11
### Added
- SLF4J for logging

### Fixed
- Dockerfile create directories in /storage/resource/

### Changed
- move some function in Connect_util to resue in other plugins

## [1.0.2] - 2025-02-28
### Added
- write and read MLLP
- socket connection with reconnect
- listenIncomingMessage

### Changed
- Dockerfile, problem with storage and java version

## [1.0.1] - 2024-12-05
### Changed
- prepared for test ISO with LabBook 

## [1.0.0] - 2023-12-15
### Added
- Run a Jetty server with REST API managed by Jersey Library
- Logs in file labbook_connect.log
- Makefile to build, run, stop or clean the container
- HAPI HL7 library
- analyzer plugin system with external JAR file
- load instance of analyzer with TOML setting file
- create directories for lab27, lab29, mapping and archives for each instance of analyzer
- Java documentation