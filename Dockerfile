FROM gcr.io/distroless/java17-debian11:nonroot

WORKDIR /app

COPY ./target/ebxml-processor*.jar app.jar

CMD ["/app/app.jar"]
