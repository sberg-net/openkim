# QuickStart Guide
Kurze Beschreibung zum Start von OpenKIM auf Basis der SpringBoot JAR.

## Veraussetzungen
* OS: Windows, Linux, Mac
* JavaVM: ab Version 11 (z.B. openjdk 14.0.2)
* Console / Terminal

## Download
* Ordner `openkim` anlegen (Ort deiner Wahl)
* neueste Version der JAR [vom OpenKIM Projekt herunterladen][1] z.B. [OpenKIM-0.9.0][2]
* Die Datei `openkim-$VERSION.jar` in den Ordner `openkim` kopieren

## OpenKIM starten
* Console / Terminal öffnen
* in Ordner `openkim` wechseln
* folgendes Commando absetzen (die Console bleibt offen und OpenKIM log in die Console):
```shell
java -jar openkim-*.jar

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v2.6.3)
```

## OpenKIM öffnen und anmelden
* Browser öffnen und folgende URL eingeben: http://localhost:8080
* auf der Startseite oben Rechts auf `Anmelden` klicken
    * User: admin Password: admin
* Seite "Minimale Konfiguration" öffnet sich

[1]: https://github.com/sberg-net/openkim/releases
[2]: https://github.com/sberg-net/openkim/releases/tag/OpenKIM-0.9.0
