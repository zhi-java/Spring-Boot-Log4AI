# Runtime image: copies host-built *-standalone.jar (does NOT pull maven:* from Docker Hub).
# Prerequisite: mvn -DskipTests package  (creates target/spring-boot-log4ai-*-standalone.jar)
# Local:  mvn -DskipTests package && docker build -t your-tag .
# CI:     GitHub Actions runs mvn package before docker build (see docker-publish.yml)

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gosu \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1000 log4ai \
    && useradd -u 1000 -g log4ai -m -d /app log4ai

WORKDIR /app
COPY target/spring-boot-log4ai-*-standalone.jar /app/app.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh \
    && chown log4ai:log4ai /app/app.jar

# 入口以 root 修正挂载卷权限后，gosu 降权为 log4ai 运行 JVM（见 docker-entrypoint.sh）
USER root
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -sf http://127.0.0.1:8080/log4ai/index.html > /dev/null || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]
