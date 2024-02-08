FROM ghcr.io/navikt/baseimages/temurin:17
COPY init/init.sh /init-scripts/
COPY build/libs/app.jar ./
