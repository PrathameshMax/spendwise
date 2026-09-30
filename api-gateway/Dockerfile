# syntax=docker/dockerfile:1
#
# Multi-stage build (Milestone 9) — same shape as auth-service/Dockerfile,
# which carries the full rationale for every instruction below (stage
# separation, -pl/-am, container-aware JVM flags, the healthcheck contract).
# Only the module name, jar path, port, and healthcheck URL differ per service.
#
# api-gateway runs on WebFlux/Netty rather than the servlet stack every other
# service uses, but spring-boot-starter-actuator still exposes the same
# /actuator/health endpoint on it, so the healthcheck instruction needs no
# special casing.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl api-gateway -am -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
COPY --from=build /workspace/api-gateway/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
