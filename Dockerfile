# 独立可执行包：*-standalone.jar（Log4AiStandaloneApplication）
# 日志分析依赖「容器内可见路径」：请用 volume 挂载宿主机日志目录，并与 logging.file / log4ai.logs 配置一致。
#
# 构建阶段与运行时使用 Debian/Ubuntu 系镜像（非 Alpine），在部分网络环境下拉取 manifest 比 alpine 更稳定。
# 若仍无法访问 docker.io，请在 Docker Desktop 配置镜像加速或使用 CI（GitHub Actions）构建。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1000 log4ai \
    && useradd -u 1000 -g log4ai -m -d /app log4ai

WORKDIR /app
COPY --from=build /src/target/spring-boot-log4ai-*-standalone.jar /app/app.jar

USER log4ai:log4ai
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -sf http://127.0.0.1:8080/log4ai/index.html > /dev/null || exit 1

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
