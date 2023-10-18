# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

## [0.19.2]

### Fixes
- Startseite kann nicht aufgerufen werden (thymeleaf template exeception, Änderungen in Version 3.1)

## [0.19.1]

### Security
- Vulnerabilities updates
  - spring-boot-parent
  - webjars-locator
  - snakeyaml

## [0.19.0]

### Added

- arm64 docker image to dockerHub

### Changed

- refactor release action

## [0.18.1]

### Fixed

- integration des BouncyCastleJsseProvider und bouncy castle versionen 1.75


## [0.17.0]

### Added

- Fehlerbehebungen
- 6 Pipeline-Operationen, die der Nutzer über die OpenKIM-Oberfläche testen kann
  - Verschlüsseln eines beliebigen Textes mit einem Konnektor der Wahl, 
    der aber in OpenKIM konfiguriert sein muss
  - Entschlüsseln eines Textes
  - Senden einer Mail über das OpenKIM-SMTP-Gateway
  - Empfangen einer Mail über das OpenKIM-POP3-Gateway
  - Signieren und Verschlüsseln einer Mail (ohne Versand) und Ausgabe der Mail
  - Entschlüsseln und Signaturüberprüfung einer verschlüsselten Mail, die der Nutzer komplett angeben muss

## [0.16.0]

### Added

- Integration von KAS
- Fehlerbehebungen

## [0.15.0]

### Added

- Integrieren von Testmöglichkeiten für alle gewünschten Pipeline-Operationen
- Aufruf der Tests von Weboberfläche

## [0.14.1]

### Fixed

- Pipeline-Operationen


## [0.14.0]

### Added

- OpenKIM im Modus keine TI getestet
- SMTP/POP3 - Protokoll des Gateways getestet


## [0.13.1]

### Fixed

- Pipeline-Struktur


## [0.13.0]

### Added

- Hinzufügen einer Pipeline-Struktur für die Operationen (z.b. Signieren einer Mail)
- bessere Test- und Erweiterbarkeit
  - besseres Handling der Komplexität
  - Testen von drei Szenarien
    - Testen komplet ohne TI mit "handelsüblichen" Mailservern -> Checken, ob der POP3/SMTP-Stack funktioniert
    - Testen ohne Fachdienst-Servern, mit Konnektor und mit "handelsüblichen" Mailservern -> Checken, ob die Konnektor-Kommunikation funktioniert
    - Testen "Fullstack" -> mit Fachdienst-Servern und Konnektor 
- Weitere Umbauarbeiten

## [0.12.0]

### Added

- Hinzufügen einer Pipeline-Struktur für die Operationen (z.b. Signieren einer Mail)
- bessere Test- und Erweiterbarkeit


## [0.9.0]

### Added

- Erstellen des Git-Repositorys
- Github Actions
- Dockerfile inklusive build und push zu [Dockerhub]
