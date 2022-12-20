# OpenKIM Docker Image

openKIM kann voll funktionsfähig in einer Docker oder Kubernetes Umgebung verwendet werden.<br>
Dazu stellen wir openKIM Docker Images auf Basis der openKIM Releases zu Verfügung.

## Start openKIM docker

```
docker run --name openKIM -p 8080:8080 sbergit/openkim:latest
```

### Container Volumes

| Container Verzeichnis | Inhalt                                                    | 
|-----------------------|-----------------------------------------------------------|
| /data                 | persistante und flüchtige Daten<br> z.B. <li>Anmeldedaten |
| /logs                 | openKIM logfiles                                          |

## Docker Compose Beispiel

```yaml
version: '3.1'
services:
  openkim:
    image: sbergit/openkim:latest
    container_name: openKIM
    restart: always
    environment:
      TZ: Europe/Berlin
    volumes:
      - ./vol/data:/data
      - ./vol/logs:/logs
    ports:
      - "8080:8080"
```

## Docker Evironment Parameter

| Name          | Beschreibung            | Values                             | default       |
|---------------|-------------------------|------------------------------------|---------------|
| TZ            | Timezone des Containers | tzdata zones (z.B. Europe/Berlin)  | Europe/Berlin |
| LOG-LEVEL     | rool Log-Level          | ERROR,INFO,DEBUG                   | INFO          |
| HTTP-PORT     | HTTP GUI Server Port    | Port Nummer (z.B. 8080)            | 8080          |
| VZD-LDAP-BASE | VZD LDAP Base           | String                             | dc=data,dc=vz |

> Bei nicht gesetzten Parametern werden die default Werte gesetzt! 