FROM ghcr.io/navikt/baseimages/temurin:17
COPY init*/.* /init-scripts/
COPY build/libs/app.jar ./
