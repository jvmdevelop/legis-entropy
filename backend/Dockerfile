FROM gradle:9.4.1-jdk26-alpine AS builder
WORKDIR /app

ARG SERVICE_NAME

COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./

RUN mkdir -p auth-service chat-service gateway-service eureka-server user-service \
             llm-brain-service workspace-service graphs-service voice-service \
             template-service law-service situation-service document-service

COPY ${SERVICE_NAME}/ ${SERVICE_NAME}/

RUN gradle :${SERVICE_NAME}:bootJar --no-daemon -x test

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

RUN apk add --no-cache libstdc++ gcompat

ARG SERVICE_NAME

COPY --from=builder /app/${SERVICE_NAME}/build/libs/${SERVICE_NAME}-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]