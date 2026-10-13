# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM node:20-alpine AS ui
WORKDIR /src/ui
COPY ui/package.json ui/package-lock.json ui/.npmrc ./
RUN npm ci --no-audit --no-fund
COPY ui/ ./
RUN npm run build

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS jar
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
COPY --from=ui /src/ui/dist ui/dist
RUN ./gradlew --no-daemon -q shadowJar

FROM eclipse-temurin:21-jdk AS runtime
RUN jlink \
      --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.management,jdk.unsupported,jdk.zipfs \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /jre \
 && mkdir -p /data

FROM gcr.io/distroless/base-debian12:nonroot
LABEL org.opencontainers.image.title="MockHub" \
      org.opencontainers.image.description="Team mock server for REST and SOAP built on WireMock, with a web UI" \
      org.opencontainers.image.source="https://github.com/monk-developer-tools/mockhub"
COPY --from=runtime /jre /opt/java
COPY --from=runtime --chown=65532:65532 /data /data
COPY --from=jar /src/build/libs/mockhub.jar /opt/mockhub/mockhub.jar
ENV PORT=8080 \
    MOCKHUB_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
VOLUME ["/data"]
EXPOSE 8080
USER 65532:65532
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD ["/opt/java/bin/java", "-XX:TieredStopAtLevel=1", "-cp", "/opt/mockhub/mockhub.jar", "io.mockhub.HealthCheck"]
ENTRYPOINT ["/opt/java/bin/java", "-jar", "/opt/mockhub/mockhub.jar"]
