# syntax=docker/dockerfile:1.7
# Multi-target build: `--target server` (Quarkus app + PWA) or `--target librus-mock` (offline Librus mock).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
RUN ./gradlew --no-daemon --version >/dev/null
COPY librus-client librus-client
COPY librus-mock librus-mock
COPY server server
RUN --mount=type=cache,target=/root/.gradle/caches \
    ./gradlew --no-daemon --console=plain :server:quarkusBuild :librus-mock:installDist -x test

FROM eclipse-temurin:25-jre AS librus-mock
RUN useradd --system --uid 10001 jlibrus
COPY --from=build /src/librus-mock/build/install/librus-mock /opt/librus-mock
USER 10001
EXPOSE 8090
ENV MOCK_PORT=8090
ENTRYPOINT ["/opt/librus-mock/bin/librus-mock"]

FROM eclipse-temurin:25-jre AS server
RUN useradd --system --uid 10001 jlibrus && mkdir -p /data && chown 10001 /data
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
