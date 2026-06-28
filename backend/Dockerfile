FROM gradle:9.4.1-jdk26-alpine AS builder
WORKDIR /app

COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./

ARG SERVICE_NAME

COPY ${SERVICE_NAME}/ ${SERVICE_NAME}/

RUN ./gradlew :${SERVICE_NAME}:bootJar --no-daemon -x test -q

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

ARG SERVICE_NAME

COPY --from=builder /app/${SERVICE_NAME}/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]