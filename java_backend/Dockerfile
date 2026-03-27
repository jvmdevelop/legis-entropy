FROM gradle:8.8-jdk21 AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon --quiet || true

COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
