# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.0.13] - 2026-02-03
### Changed
- Improved production readiness by cleaning up Connect APIs, comments, and documentation

## [1.0.12] - 2025-12-22
### Added
- analyzer mappingPath support (loaded from settings) and common TOML mapping loader in Connect_util

## [1.0.11] - 2025-12-01
### Added
- automatic stopListening() call before reloading an analyzer plugin

## [1.0.10] - 2025-11-27
### Changed
- sanitized all user-controlled log inputs to prevent log-injection issues

## [1.0.10] - 2025-11-04
### Changed
- version number

## [1.0.9] - 2025-10-22
### Fixed
- processes URLs from the list with or without the word “external”. Maintains compatibility with 3.5.x.

## [1.0.8] - 2025-09-25
### Changed
- version number

## [1.0.7] - 2025-09-17
### Added
- send_hl7_msg is https compatible and non authorises self-signed certificates (possible to change this, but not secured)

### Changed
- adds port opening 12345

## [1.0.6] - 2025-07-17
### Fixed
- send_hl7_msg

## [1.0.5] - 2025-07-10
### Changed
- content-type of lab28 webservice to text/plain
- pattern of log with datetime

### Fixed
- reloading plugins does not update the instance from the modified values in the configuration file

## [1.0.4] - 2025-04-28
### Added
- processing for new connection type socket_E1381

## [1.0.3] - 2025-03-13
### Added
- SLF4J for logging
- create directories /storage/resource/connect...

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