@echo off
chcp 65001 >nul
setlocal EnableExtensions

REM =============================================================================
REM Spring-Boot-Log4AI：本地构建 Docker 镜像并推送到 ghcr.io
REM 用法（在项目根目录执行，或双击前先把「当前目录」设为项目根）：
REM   scripts\ghcr-build-push.bat
REM   scripts\ghcr-build-push.bat 0.1.0 zhi-java
REM 依赖环境变量（推送前必设其一）：
REM   set GITHUB_PAT=ghp_你的令牌
REM 或先登录一次：docker login ghcr.io -u 用户名
REM =============================================================================

cd /d "%~dp0.."
if not exist "Dockerfile" (
  echo [错误] 未找到 Dockerfile，请从本仓库根目录运行，或检查脚本路径。
  exit /b 1
)

set "VERSION=%~1"
set "GH_USER=%~2"

if "%VERSION%"=="" set "VERSION=0.1.0"
if "%GH_USER%"=="" (
  set /p "GH_USER=GitHub 用户名（小写，与 ghcr.io 路径一致）: "
)
if "%GH_USER%"=="" (
  echo [错误] 未指定 GitHub 用户名。
  exit /b 1
)

set "IMAGE=ghcr.io/%GH_USER%/log4ai-server:%VERSION%"

echo.
echo [1/3] docker build  %IMAGE%
echo.
docker build -t "%IMAGE%" .
if errorlevel 1 (
  echo [错误] 构建失败。若拉取基础镜像超时，请配置 Docker 镜像加速或使用 GitHub Actions 构建。
  exit /b 1
)

echo.
echo [2/3] docker login ghcr.io
if "%GITHUB_PAT%"=="" (
  echo 未设置 GITHUB_PAT，将尝试交互登录（需已安装 docker credential 或手动输入密码）。
  docker login ghcr.io -u "%GH_USER%"
  if errorlevel 1 exit /b 1
) else (
  echo %GITHUB_PAT%| docker login ghcr.io -u "%GH_USER%" --password-stdin
  if errorlevel 1 (
    echo [错误] 登录失败。请检查 PAT 是否含 read/write packages 权限，或改用: set GITHUB_PAT= 清空后交互登录。
    exit /b 1
  )
)

echo.
echo [3/3] docker push  %IMAGE%
docker push "%IMAGE%"
if errorlevel 1 exit /b 1

echo.
echo 完成: %IMAGE%
exit /b 0
