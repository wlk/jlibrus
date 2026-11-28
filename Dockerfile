# syntax=docker/dockerfile:1.7
# Multi-target build: `--target server` (Quarkus app + PWA) or `--target librus-mock` (offline Librus mock).

# The build output is portable JVM bytecode, so it is built once on the builder's own platform: a multi-platform
# release does not run Gradle under QEMU. Only the runtime stages below are per target platform.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
# Dependency layer: build scripts only, so it is rebuilt (and Maven Central asked) only when they change. The
# downloads live in the layer, not in a --mount=type=cache directory, because only layers reach the CI (gha) cache.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY librus-client/build.gradle.kts librus-client/
COPY librus-mock/build.gradle.kts librus-mock/
COPY server/build.gradle.kts server/
RUN ./gradlew --no-daemon --console=plain resolveDependencies :server:quarkusBuildAppModel
COPY librus-client librus-client
COPY librus-mock librus-mock
COPY server server
# --offline: a dependency missing from the layer above fails here instead of being downloaded on every source change.
RUN ./gradlew --no-daemon --console=plain --offline :server:quarkusBuild :librus-mock:installDist -x test

# Runtime base: Ubuntu security updates published after the base image was built, and no pebble
# (an unused service manager whose bundled Go runtime trails security releases).
FROM eclipse-temurin:25-jre AS runtime
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* /usr/bin/pebble \
    && useradd --system --uid 10001 jlibrus

FROM runtime AS librus-mock
COPY --from=build /src/librus-mock/build/install/librus-mock /opt/librus-mock
USER 10001
EXPOSE 8090
ENV MOCK_PORT=8090
ENTRYPOINT ["/opt/librus-mock/bin/librus-mock"]

FROM runtime AS server
RUN mkdir -p /data && chown 10001 /data
WORKDIR /opt/jlibrus
COPY --from=build --chown=10001 /src/server/build/quarkus-app/lib/ lib/
COPY --from=build --chown=10001 /src/server/build/quarkus-app/app/ app/
COPY --from=build --chown=10001 /src/server/build/quarkus-app/quarkus/ quarkus/
COPY --from=build --chown=10001 /src/server/build/quarkus-app/quarkus-run.jar quarkus-run.jar
USER 10001
VOLUME /data
EXPOSE 8080
ENV DB_URL=jdbc:sqlite:/data/jlibrus.db \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /q/health/ready HTTP/1.0\\r\\n\\r\\n' >&3 && head -1 <&3 | grep -q ' 200 '"]
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /opt/jlibrus/quarkus-run.jar"]
