# syntax=docker/dockerfile:1
#
# Multi-stage build (Milestone 9) — the reference Dockerfile for the platform;
# every other service's Dockerfile is this same shape with only the `-pl`
# module, the `COPY --from=build` jar path, the exposed port, and the
# healthcheck URL changed, so the rationale below is written once here rather
# than repeated eight times.
#
# STAGE 1 — build. Needs the full JDK plus Maven, and (transiently) the whole
# local dependency cache — several hundred MB that must never reach the
# runtime image. The build context is the monorepo ROOT, not this directory
# (see docker-compose.yml's `build.context: .`): `-am` (also-make) requires
# every reactor module's POM to be on disk to resolve `spendwise-common`, the
# one module every service in this codebase depends on since Milestone 1.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
# -pl restricts the artifact actually being packaged to this one module; -am
# still builds spendwise-common first since this module depends on it —
# together they avoid a full nine-module reactor build to produce one
# service's jar. Tests are skipped deliberately: this stage has no Docker
# daemon of its own to run the Testcontainers-backed integration tests this
# service ships (CredentialRepositoryIT), and test execution belongs to a CI
# pipeline stage, not an image build.
RUN mvn -B -pl auth-service -am -DskipTests clean package

# STAGE 2 — runtime. Eclipse Temurin's own -jre-alpine variant: no javac, no
# Maven, no source, no dependency cache — just the JRE needed to run an
# already-compiled fat jar, on the smallest well-supported base OS available
# (Q39 — this stage separation is what actually makes the image lean).
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Container-aware JVM flags (Q40 — avoiding OOMKilled). JDK 21 already detects
# the cgroup memory limit docker-compose.yml's `deploy.resources.limits.memory`
# imposes (UseContainerSupport has been on by default since JDK 10), but its
# *default* MaxRAMPercentage (25%) leaves most of a memory-constrained
# container unused by the heap, which just pushes a Spring Boot app into
# constant GC pressure instead. 75% hands the heap the large majority of the
# container's quota while still leaving headroom for metaspace, thread
# stacks, and direct buffers — the actual cause of most "the heap graph looks
# fine but the container still got OOMKilled" incidents, since the kernel's
# OOM killer accounts for the JVM's *total* RSS, not just heap.
# ExitOnOutOfMemoryError turns a corrupted, half-alive JVM into a clean, fast
# process exit instead of a silent hang, so Compose's restart policy actually
# has something to react to.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

COPY --from=build /workspace/auth-service/target/*.jar app.jar

EXPOSE 8081

# Milestone 9's health-check wait condition: docker-compose.yml's `depends_on:
# condition: service_healthy` (api-gateway waits on this service) polls
# exactly this instruction, so a dependent container only starts once Spring
# Boot's own /actuator/health reports UP — not merely once the process has
# started, which for a JVM bootstrapping a Spring context can be several
# seconds apart. wget's `--spider` mode makes a HEAD-equivalent request only;
# Alpine's busybox ships wget already, so no extra package install is needed
# to keep the runtime image minimal.
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
