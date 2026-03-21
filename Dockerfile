# Runtime image: copies host-built *-standalone.jar (does NOT pull maven:* from Docker Hub).
# Prerequisite: mvn -DskipTests package  (creates target/spring-boot-log4ai-*-standalone.jar)
# Local:  mvn -DskipTests package && docker build -t your-tag .
# CI:     GitHub Actions runs mvn package before docker build (see docker-publish.yml)
#
# 进程默认以 root 运行（见 docker-entrypoint.sh），便于挂载目录权限与业务日志属主不一致时的只读分析。
# 若需非 root，可在 compose 中 user: "1000:1000" 并保证日志目录对 uid 1000 可读。

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/spring-boot-log4ai-*-standalone.jar /app/app.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

USER root
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -sf http://127.0.0.1:8080/log4ai/index.html > /dev/null || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]
