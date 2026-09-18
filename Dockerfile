FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:da227476f495f2609b8913ef7f366acb0911d9f2202d5e5cdbd0b25eb97c9030
COPY build/libs/app.jar /app/app.jar

WORKDIR /app
USER nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
