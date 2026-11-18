# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
RUN --mount=type=cache,target=/root/.m2 mvn -B -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.22.0 \
    -DoutputDirectory=/otel
RUN javac -d /health infra/docker/Healthcheck.java

# A imagem oficial fornece o Runtime Interface Emulator para invocações locais.
FROM public.ecr.aws/lambda/java:21 AS lambda
ARG MODULE
COPY --from=build /build/lambdas/${MODULE}/target/${MODULE}-0.2.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/handler.jar

FROM eclipse-temurin:21-jre AS spring-service
WORKDIR /app
ARG MODULE
RUN groupadd -r inpay && useradd -r -g inpay inpay
COPY --from=build /build/services/${MODULE}/target/${MODULE}-0.2.0-SNAPSHOT.jar /app/app.jar
COPY --from=build /otel/opentelemetry-javaagent-2.22.0.jar /app/otel.jar
COPY --from=build /health /app/health
USER inpay
ENTRYPOINT ["java", "-javaagent:/app/otel.jar", "-jar", "/app/app.jar"]
