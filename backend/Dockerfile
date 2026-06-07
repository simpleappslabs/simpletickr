FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY openapi.yaml openapi.yaml
COPY backend/ backend/
WORKDIR /app/backend
RUN --mount=type=cache,target=/root/.gradle ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
