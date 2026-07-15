# --- Build stage ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY order-service/pom.xml /workspace/order-service/pom.xml
COPY saga-orchestrator/pom.xml /workspace/saga-orchestrator/pom.xml
COPY payment-service/pom.xml /workspace/payment-service/pom.xml
COPY inventory-service/pom.xml /workspace/inventory-service/pom.xml
COPY flaky-external-service/pom.xml /workspace/flaky-external-service/pom.xml

COPY flaky-external-service/src /workspace/flaky-external-service/src

RUN mvn -q -pl flaky-external-service -am -DskipTests package

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/flaky-external-service/target/flaky-external-service.jar /app/app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
