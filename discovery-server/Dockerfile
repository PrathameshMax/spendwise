# syntax=docker/dockerfile:1
#
# Multi-stage build (Milestone 9) — same shape as auth-service/Dockerfile,
# which carries the full rationale for every instruction below (stage
# separation, -pl/-am, container-aware JVM flags, the healthcheck contract).
# Only the module name, jar path, port, and healthcheck URL differ per service.
# Milestone 13 fix — BuildKit Maven cache mount and non-root USER, same as
# every other service; see auth-service/Dockerfile for the full rationale.
#
# discovery-server has no datasource and no config-server dependency of its
# own (it's the registry other services look up, not a config consumer), but
# it still depends on spendwise-common via the Maven reactor like every other
# module, so it still needs -am here.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl discovery-server -am -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
RUN addgroup -S spendwise && adduser -S -G spendwise spendwise
COPY --from=build /workspace/discovery-server/target/*.jar app.jar
RUN chown spendwise:spendwise app.jar
USER spendwise
EXPOSE 8761
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
