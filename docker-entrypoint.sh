#!/bin/sh
# 默认以 root 启动 JVM，便于读取宿主机挂载的 root 属主日志目录（生产亦可配合宿主机 chmod/chown 仍用非 root 镜像）。
set -e
mkdir -p /app/.log4ai
exec java $JAVA_OPTS -jar /app/app.jar
