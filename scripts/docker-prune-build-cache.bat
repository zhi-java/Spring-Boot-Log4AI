@echo off
REM Run when docker build fails with: failed size validation / failed precondition on layer-sha256
REM Then retry: scripts\ghcr-build-push.bat ...

echo Pruning Docker build cache...
docker builder prune -f
if errorlevel 1 (
  echo [ERR] docker builder prune failed.
  exit /b 1
)
echo OK. Retry your docker build. If it still fails, try: set DOCKER_BUILDKIT=0
exit /b 0
