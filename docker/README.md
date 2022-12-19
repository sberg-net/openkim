# OpenKIM Docker Image

## Docker Evironment Parameter

| Name          | Beschreibung            | Values                             | default       |
|---------------|-------------------------|------------------------------------|---------------|
| TZ            | Timezone des Containers | tzdata zones (z.B. Europe/Berlin)  | Europe/Berlin |
| LOG-LEVEL     | rool Log-Level          | ERROR,INFO,DEBUG                   | INFO          |
| HTTP-PORT     | HTTP GUI Server Port    | Port Nummer (z.B. 8080)            | 8080          |
| VZD-LDAP-BASE | VZD LDAP Base           | String                             | dc=data,dc=vz |

## Start openKIM docker

```
docker run --name openKIM -p 8080:8080 sbergit/openkim:latest
```

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
    ports:
      - 8080:8080
```