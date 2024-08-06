FROM ghcr.io/navikt/baseimages/temurin:21-appdynamics
COPY init/init.s[h] /init-scripts/
COPY build/libs/app.jar ./

#ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Doracle.jdbc.javaNetNio=false -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -Djava.security.debug=all"
