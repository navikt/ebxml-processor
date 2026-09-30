FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:9fdf8d6368b48fb2a7cc8202483f09cd3e21ba034230b6b43e5822d345cbc548
COPY build/libs/app.jar /app/app.jar

WORKDIR /app
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
