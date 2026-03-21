@echo off
REM ghcr-build-push.bat - build image and push to ghcr.io
REM Default Dockerfile only needs JRE; build JAR on host first (mvn). No maven Docker image.
REM Run:  cd repo-root   then   scripts\ghcr-build-push.bat [version] [github_user]
REM Before push: set GITHUB_PAT=ghp_xxx  OR interactive docker login
REM NOTE: ASCII-only for CMD on Chinese Windows.

setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "Dockerfile" (
  echo [ERR] Dockerfile not found. Run from repository root ^(cd .. from scripts^).
  exit /b 1
)

set "JARFILE="
for %%F in (target\spring-boot-log4ai-*-standalone.jar) do set "JARFILE=%%F"
if not defined JARFILE (
  echo [INFO] No standalone JAR. Running mvn -DskipTests package ...
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo [ERR] mvn package failed. Install JDK 17 + Maven on PATH.
    exit /b 1
  )
)

set "VERSION=%~1"
set "GH_USER=%~2"

if "%VERSION%"=="" set "VERSION=0.1.0"
if "%GH_USER%"=="" set /p "GH_USER=GitHub username [lowercase]: "

if "%GH_USER%"=="" (
  echo [ERR] GitHub username is required.
  exit /b 1
)

set "IMAGE=ghcr.io/%GH_USER%/log4ai-server:%VERSION%"
set "IMAGE_LATEST=ghcr.io/%GH_USER%/log4ai-server:latest"

echo.
echo [1/3] docker build  %IMAGE%  +  :latest
echo.
set "BUILD_EXTRA="
if "%DOCKER_BUILD_NO_CACHE%"=="1" set "BUILD_EXTRA=--no-cache"
docker build %BUILD_EXTRA% -t "%IMAGE%" -t "%IMAGE_LATEST%" .
if errorlevel 1 (
  echo [ERR] docker build failed.
  exit /b 1
)

echo.
echo [2/3] docker login ghcr.io
docker logout ghcr.io 2>nul
if "%GITHUB_PAT%"=="" (
  echo.
  echo --- GHCR login help ---
  echo Username: GitHub LOGIN name - must match GH_USER above. NOT email.
  echo Password: Personal Access Token starting with ghp_. NOT GitHub web password.
  echo Create PAT: GitHub - Settings - Developer settings - Fine-grained or Classic token.
  echo Classic scopes: read:packages + write:packages
  echo If org uses SSO: authorize token for that org on the token page.
  echo Easier: set GITHUB_PAT=ghp_xxx in this CMD window, then re-run this script.
  echo -------------------------
  echo.
  echo Interactive login for user: %GH_USER%
  docker login ghcr.io -u "%GH_USER%"
  if errorlevel 1 (
    echo [ERR] Login denied. See messages above. Or set GITHUB_PAT=ghp_xxx and run again.
    exit /b 1
  )
) else (
  echo %GITHUB_PAT%| docker login ghcr.io -u "%GH_USER%" --password-stdin
  if errorlevel 1 (
    echo [ERR] docker login failed. Check PAT has read/write packages.
    exit /b 1
  )
)

echo.
echo [3/3] docker push  %IMAGE%  and  :latest
docker push "%IMAGE%"
if errorlevel 1 exit /b 1
docker push "%IMAGE_LATEST%"
if errorlevel 1 exit /b 1

echo.
echo OK: %IMAGE%
echo OK: %IMAGE_LATEST%
exit /b 0
