FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:092cfaeb402d0a0ac189967f488493f46bc0aea250eb886a104146f581526395
COPY build/libs/app.jar /app/app.jar

WORKDIR /app
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
