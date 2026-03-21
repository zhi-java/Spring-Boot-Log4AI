# 独立可执行包：*-standalone.jar（Log4AiStandaloneApplication）
# 日志分析依赖「容器内可见路径」：请用 volume 挂载宿主机日志目录，并与 logging.file / log4ai.logs 配置一致。

FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -g 1000 log4ai \
    && adduser -u 1000 -G log4ai -h /app -D log4ai

WORKDIR /app
COPY --from=build /src/target/spring-boot-log4ai-*-standalone.jar /app/app.jar

USER log4ai:log4ai
EXPOSE 8080

# JVM 与 Spring 可通过环境变量覆盖（如 JAVA_TOOL_OPTIONS、SPRING_*）
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 默认工作目录 /app；控制台保存的 settings 在 /app/.log4ai/，需要持久化时可挂载卷
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -sf http://127.0.0.1:8080/log4ai/index.html > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
