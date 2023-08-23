FROM gcr.io/distroless/java17-debian11:nonroot

WORKDIR /app

# TODO change to match the path to your "fat jar"
COPY build/libs/app-all.jar .

CMD ["/app/app-all.jar"]
