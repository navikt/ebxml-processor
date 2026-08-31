FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:3e34d9a6194f53721f4f81a9f01bc118d3a7a2d17eb8079d1bd0be55ae789214
COPY build/libs/app.jar /app/app.jar

WORKDIR /app
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
