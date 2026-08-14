FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY order-generator/target/order-generator-1.0.0.jar app.jar
RUN mkdir -p /app/data /app/output
VOLUME ["/app/data", "/app/output"]
ENTRYPOINT ["java", "-jar", "app.jar"]
