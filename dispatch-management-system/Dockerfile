FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build/dispatch-management-system

COPY dispatch-management-system/.mvn/ .mvn
COPY dispatch-management-system/mvnw dispatch-management-system/pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY proto /build/proto
COPY dispatch-management-system/src ./src
COPY dispatch-management-system/openapi.yaml ./openapi.yaml
RUN ./mvnw package -Dmaven.test.skip=true -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /build/dispatch-management-system/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
