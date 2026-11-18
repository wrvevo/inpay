FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
ARG MODULE
RUN test -n "$MODULE" && mvn -B -pl services/$MODULE -am package -DskipTests
RUN cp services/$MODULE/target/$MODULE-0.1.0-SNAPSHOT.jar /app.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r gateway && useradd -r -g gateway gateway
COPY --from=build /app.jar /app/app.jar
USER gateway
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
