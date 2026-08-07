FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:1e87e7c04355f6817e4bf034ee748f0d306ae9be68ea47d3b41f44846d47bc54
COPY build/libs/app.jar /app/app.jar

WORKDIR /app
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
