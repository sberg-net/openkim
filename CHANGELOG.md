# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
```
```

### Changed
```
```

### Fixed
```
```

## [0.17.0]

### Added
```
- Fehlerbehebungen
- 6 Pipeline-Operationen, die der Nutzer über die OpenKIM-Oberfläche testen kann
  - Verschlüsseln eines beliebigen Textes mit einem Konnektor der Wahl, 
    der aber in OpenKIM konfiguriert sein muss
  - Entschlüsseln eines Textes
  - Senden einer Mail über das OpenKIM-SMTP-Gateway
  - Empfangen einer Mail über das OpenKIM-POP3-Gateway
  - Signieren und Verschlüsseln einer Mail (ohne Versand) und Ausgabe der Mail
  - Entschlüsseln und Signaturüberprüfung einer verschlüsselten Mail, die der Nutzer komplett angeben muss
```

### Changed
```
```

### Fixed
```
```

## [0.16.0]

### Added
```
- Integration von KAS
- Fehlerbehebungen
```

### Changed
```
```

### Fixed
```
```

## [0.15.1]

### Added
```
```

### Changed
```
```

### Fixed
```
```

## [0.15.0]

### Added
```
- Integrieren von Testmöglichkeiten für alle gewünschten Pipeline-Operationen
- Aufruf der Tests von Weboberfläche
```

### Changed
```
```

### Fixed
```
```

## [0.14.1]

### Added
```
```

### Changed
```
```

### Fixed
```
- Pipeline-Operationen
```

## [0.14.0]

### Added
```
- OpenKIM im Modus keine TI getestet
- SMTP/POP3 - Protokoll des Gateways getestet
```

### Changed
```
```

### Fixed
```
```

## [0.13.1]

### Added
```
```

### Changed
```
```

### Fixed
```
- Pipeline-Struktur
```

## [0.13.0]

### Added
```
- Hinzufügen einer Pipeline-Struktur für die Operationen (z.b. Signieren einer Mail)
- bessere Test- und Erweiterbarkeit
  - besseres Handling der Komplexität
  - Testen von drei Szenarien
    - Testen komplet ohne TI mit "handelsüblichen" Mailservern -> Checken, ob der POP3/SMTP-Stack funktioniert
    - Testen ohne Fachdienst-Servern, mit Konnektor und mit "handelsüblichen" Mailservern -> Checken, ob die Konnektor-Kommunikation funktioniert
    - Testen "Fullstack" -> mit Fachdienst-Servern und Konnektor 
- Weitere Umbauarbeiten
```

### Changed
```
```

### Fixed
```
```

## [0.12.0]

### Added
```
- Hinzufügen einer Pipeline-Struktur für die Operationen (z.b. Signieren einer Mail)
- bessere Test- und Erweiterbarkeit
```

### Changed
```
```

### Fixed
```
```

## [0.11.0]

### Added
```
```

### Changed
```
```

### Fixed
```
```

## [0.9.0]

### Added
```
- Erstellen des Git-Repositorys
- Github Actions
- Dockerfile inklusive build und push zu [Dockerhub]
```

### Changed
```
```

### Fixed
```
```

[unreleased]: https://github.com/sberg-net/openkim/compare/0.12.0...HEAD
[0.12.0]: https://github.com/sberg-net/openkim/releases/tag/OpenKIM-0.12.0
[0.11.0]: https://github.com/sberg-net/openkim/releases/tag/OpenKIM-0.11.0
[0.10.0-dev1]: https://github.com/sberg-net/openkim/releases/tag/OpenKIM-0.10.0-dev1
[0.9.1]: https://github.com/sberg-net/openkim/releases/tag/OpenKIM-0.9.1
[0.9.0]: https://github.com/sberg-net/openkim/releases/tag/OpenKIM-0.9.0
[dockerhub]: https://hub.docker.com/repository/docker/sbergit/openkim
