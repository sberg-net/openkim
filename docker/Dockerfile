FROM amazoncorretto:8-alpine3.16-jdk

ENV APP-PROFILES="default"

RUN mkdir /opt/openkim
RUN mkdir /opt/openkim/logs
RUN mkdir /opt/openkim/data

COPY openkim.jar /opt/openkim/app.jar
RUN cd /opt/openkim

RUN apk add tzdata
RUN ls /usr/share/zoneinfo
RUN cp /usr/share/zoneinfo/Europe/Berlin /etc/localtime
RUN echo "Europe/Berlin" >  /etc/timezone

RUN apk add --no-cache bash
RUN apk add --update ttf-dejavu

ENTRYPOINT ["java","-Dspring.profiles.active=${APP-PROFILES}","-jar","/opt/openkim/app.jar"]
