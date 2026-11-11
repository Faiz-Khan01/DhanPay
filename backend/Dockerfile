# Stage 1: Build Spring Boot executable JAR with Java 17
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Minimal Java 17 runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget && \
    addgroup -S dhanpay && \
    adduser -S dhanpay -G dhanpay

COPY --from=builder /workspace/target/*.jar app.jar
RUN chown dhanpay:dhanpay app.jar

USER dhanpay
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
