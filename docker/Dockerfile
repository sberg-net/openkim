FROM amazoncorretto:11-alpine3.16-jdk

ENV APP-PROFILES="default"
ENV LOG-LEVEL="INFO"
ENV HTTP-PORT="8080"
ENV VZD-LDAP-BASE="dc=data,dc=vzd"

RUN mkdir /opt/openkim
COPY target/openkim-*.jar /opt/openkim/app.jar

ENV TZ=Europe/Berlin
RUN apk add --no-cache tzdata bash

ENTRYPOINT ["java", \
            "-Dspring.profiles.active=${APP-PROFILES}", \
            "-Dlogging.level.root=${LOG-LEVEL}",\
            "-Dserver.port=${HTTP-PORT}",\
            "-Dspring.ldap.base=${VZD-LDAP-BASE}",\
            "-jar","/opt/openkim/app.jar"]
