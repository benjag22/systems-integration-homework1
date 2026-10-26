FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build/truck-fleet-system

COPY truck-fleet-system/gradlew ./
COPY truck-fleet-system/gradle ./gradle
COPY truck-fleet-system/build.gradle.kts truck-fleet-system/settings.gradle.kts ./
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true

COPY proto /build/proto
COPY truck-fleet-system/src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/truck-fleet-system/build/libs/*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
