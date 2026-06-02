@echo off
setlocal
cd /d "%~dp0"

set "TARGET_DIR=%LOCALAPPDATA%\CommonGroundsPOS"
set "LOG_FILE=%~dp0uninstall.log"

echo [INFO] Starting uninstall > "%LOG_FILE%"
taskkill /F /IM CommonGroundsPOS.exe >nul 2>nul

if exist "%TARGET_DIR%" (
  rmdir /S /Q "%TARGET_DIR%" >>"%LOG_FILE%" 2>&1
)

powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$desktop = [Environment]::GetFolderPath('Desktop'); $s1 = Join-Path $desktop 'CommonGroundsPOS.lnk'; $menu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Common Grounds'; $s2 = Join-Path $menu 'CommonGroundsPOS.lnk'; if (Test-Path $s1) { Remove-Item $s1 -Force }; if (Test-Path $s2) { Remove-Item $s2 -Force }; if (Test-Path $menu) { $left = Get-ChildItem $menu -Force -ErrorAction SilentlyContinue; if (-not $left) { Remove-Item $menu -Force } }" >>"%LOG_FILE%" 2>&1

echo Desinstallation terminee.
pause
exit /b 0

