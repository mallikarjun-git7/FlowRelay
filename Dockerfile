# Stage 1: Build React dashboard
FROM node:22-alpine AS frontend

WORKDIR /frontend

COPY dashboard/package*.json ./
RUN npm ci

COPY dashboard/ ./
RUN npm run build

# Stage 2: Build Spring Boot with React assets
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

COPY --from=frontend /frontend/dist/ \
    /workspace/src/main/resources/static/

RUN chmod +x gradlew && \
    ./gradlew bootJar --no-daemon

# Stage 3: Run Spring Boot
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder \
    /workspace/build/libs/queueforge-*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-Xms64m", "-Xmx320m", "-jar", "app.jar"]
