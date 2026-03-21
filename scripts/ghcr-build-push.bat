@echo off
REM ghcr-build-push.bat - build image and push to ghcr.io
REM Run from CMD:  cd repo-root   then   scripts\ghcr-build-push.bat [version] [github_user]
REM Or:  scripts\ghcr-build-push.bat 0.1.0 zhi-java
REM Before push set:  set GITHUB_PAT=ghp_xxx   OR use interactive docker login when prompted.
REM NOTE: File must be ASCII-only. CMD breaks UTF-8 without BOM on Chinese Windows.
REM If build fails with "failed size validation" / "layer-sha256 ... failed precondition":
REM   1) scripts\docker-prune-build-cache.bat
REM   2) Optional: set DOCKER_BUILDKIT=0  (legacy builder)
REM   3) Optional: set DOCKER_BUILD_NO_CACHE=1  (this script passes --no-cache to docker build)

setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "Dockerfile" (
  echo [ERR] Dockerfile not found. Run this script from the repository root ^(cd .. from scripts^).
  exit /b 1
)

set "VERSION=%~1"
set "GH_USER=%~2"

if "%VERSION%"=="" set "VERSION=0.1.0"
if "%GH_USER%"=="" set /p "GH_USER=GitHub username (lowercase): "

if "%GH_USER%"=="" (
  echo [ERR] GitHub username is required.
  exit /b 1
)

set "IMAGE=ghcr.io/%GH_USER%/log4ai-server:%VERSION%"

echo.
echo [1/3] docker build  %IMAGE%
echo.
set "BUILD_EXTRA="
if "%DOCKER_BUILD_NO_CACHE%"=="1" set "BUILD_EXTRA=--no-cache"
docker build %BUILD_EXTRA% -t "%IMAGE%" .
if errorlevel 1 (
  echo [ERR] docker build failed. Check Docker Hub access or use GitHub Actions to build.
  exit /b 1
)

echo.
echo [2/3] docker login ghcr.io
if "%GITHUB_PAT%"=="" (
  echo GITHUB_PAT not set. Using interactive login. Paste a Personal Access Token as password.
  docker login ghcr.io -u "%GH_USER%"
  if errorlevel 1 exit /b 1
) else (
  echo %GITHUB_PAT%| docker login ghcr.io -u "%GH_USER%" --password-stdin
  if errorlevel 1 (
    echo [ERR] docker login failed. Check PAT has read:packages and write:packages.
    exit /b 1
  )
)

echo.
echo [3/3] docker push  %IMAGE%
docker push "%IMAGE%"
if errorlevel 1 exit /b 1

echo.
echo OK: %IMAGE%
exit /b 0
