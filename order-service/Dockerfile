FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY order-service/pom.xml order-service/pom.xml
RUN mvn dependency:go-offline -B -pl order-service -am
COPY order-service/src order-service/src
RUN mvn package -B -pl order-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/order-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
