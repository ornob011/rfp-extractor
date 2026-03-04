FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY rfp-core/pom.xml rfp-core/
COPY rfp-service/pom.xml rfp-service/
RUN mvn dependency:go-offline -pl rfp-core,rfp-service -am -q
COPY rfp-core/src rfp-core/src
COPY rfp-service/src rfp-service/src
RUN mvn clean package -pl rfp-core,rfp-service -am -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/rfp-service/target/*.jar app.jar
RUN mkdir -p /app/rfp-storage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
