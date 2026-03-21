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
REM If Docker Hub blocks maven image (metadata / pull errors), use host Maven instead:
REM   set DOCKER_USE_PREBUILT=1
REM   then run this script (requires JDK 17 + Maven on PATH; builds JAR if missing)

setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "Dockerfile" (
  echo [ERR] Dockerfile not found. Run this script from the repository root ^(cd .. from scripts^).
  exit /b 1
)

set "DOCKER_FILE=Dockerfile"
if "%DOCKER_USE_PREBUILT%"=="1" (
  if not exist "Dockerfile.prebuilt" (
    echo [ERR] Dockerfile.prebuilt not found.
    exit /b 1
  )
  set "DOCKER_FILE=Dockerfile.prebuilt"
  set "JARFILE="
  for %%F in (target\spring-boot-log4ai-*-standalone.jar) do set "JARFILE=%%F"
  if not defined JARFILE (
    echo [INFO] No standalone JAR in target\. Running mvn -DskipTests package ...
    call mvn -q -DskipTests package
    if errorlevel 1 (
      echo [ERR] mvn package failed. Install Maven + JDK 17, or unset DOCKER_USE_PREBUILT and fix Docker Hub access.
      exit /b 1
    )
  )
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
echo [1/3] docker build -f %DOCKER_FILE%  %IMAGE%
echo.
set "BUILD_EXTRA="
if "%DOCKER_BUILD_NO_CACHE%"=="1" set "BUILD_EXTRA=--no-cache"
docker build %BUILD_EXTRA% -f "%DOCKER_FILE%" -t "%IMAGE%" .
if errorlevel 1 (
  echo [ERR] docker build failed. If maven:* image metadata/pull failed, try:
  echo   set DOCKER_USE_PREBUILT=1
  echo   scripts\ghcr-build-push.bat
  echo Or use GitHub Actions to build.
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
