# Multi-stage build: build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper and POMs first (layer cache for dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/payment-service/pom.xml services/payment-service/pom.xml

# Download dependencies (cached if POMs unchanged)
RUN ./mvnw -pl services/payment-service dependency:go-offline -q

# Copy source and build
COPY services/payment-service/src services/payment-service/src
RUN ./mvnw -pl services/payment-service package -DskipTests -q

# Runtime stage: minimal JRE, non-root user
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S payment && adduser -S payment -G payment
WORKDIR /app

COPY --from=builder /build/services/payment-service/target/*.jar app.jar

USER payment
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
