FROM gcr.io/distroless/java21-debian12@sha256:f995f26f78b65251a0511109b07401a5c6e4d7b5284ae73e8d6577d24ff26763
COPY build/libs/app.jar /app/app.jar
COPY build/generated/migrations /app/migrations

#ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Doracle.jdbc.javaNetNio=false -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787"

#debug sikkerhet ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Doracle.jdbc.javaNetNio=false -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -Djava.security.debug=all"

WORKDIR /app
USER nonroot
CMD [ "app.jar" ]
