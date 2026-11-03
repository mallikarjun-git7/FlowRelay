# Stage 1: Build the Spring Boot application
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew && \
    ./gradlew bootJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder \
    /workspace/build/libs/queueforge-*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-Xms64m", "-Xmx320m", "-jar", "app.jar"]
