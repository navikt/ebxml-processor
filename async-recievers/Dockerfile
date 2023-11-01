FROM gcr.io/distroless/java17-debian11:nonroot

WORKDIR /app

COPY ./target/asyncreceivers*.jar app.jar

CMD ["/app/app.jar"]
