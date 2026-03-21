#!/bin/sh
# 命名卷挂载到 /app/.log4ai 时目录常为 root 属主，非 root 进程无法写入 settings.json。
# 启动前修正属主，再降权执行 JVM（与 Dockerfile 中 log4ai 用户一致）。
set -e
mkdir -p /app/.log4ai
chown -R log4ai:log4ai /app/.log4ai
exec gosu log4ai /bin/sh -c "exec java $JAVA_OPTS -jar /app/app.jar"
