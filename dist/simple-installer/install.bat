@echo off
setlocal
cd /d "%~dp0"

set "SOURCE_DIR=%~dp0CommonGroundsPOS"
set "TARGET_DIR=%LOCALAPPDATA%\CommonGroundsPOS"
set "LOG_FILE=%~dp0install.log"

echo [INFO] Starting install > "%LOG_FILE%"

if not exist "%SOURCE_DIR%\CommonGroundsPOS.exe" (
  echo [ERROR] Missing source files. Extract ZIP then run install.bat.>>"%LOG_FILE%"
  echo Installation failed. See install.log
  pause
  exit /b 1
)

taskkill /F /IM CommonGroundsPOS.exe >nul 2>nul

if exist "%TARGET_DIR%" (
  rmdir /S /Q "%TARGET_DIR%" >>"%LOG_FILE%" 2>&1
)

robocopy "%SOURCE_DIR%" "%TARGET_DIR%" /E /R:1 /W:1 /NFL /NDL /NP /NJH /NJS >>"%LOG_FILE%" 2>&1
set "RC=%ERRORLEVEL%"
if %RC% GEQ 8 (
  echo [ERROR] Copy failed with code %RC%.>>"%LOG_FILE%"
  echo Installation failed. See install.log
  pause
  exit /b 1
)

if not exist "%TARGET_DIR%\CommonGroundsPOS.exe" (
  echo [ERROR] Installed executable missing.>>"%LOG_FILE%"
  echo Installation failed. See install.log
  pause
  exit /b 1
)

powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$w = New-Object -ComObject WScript.Shell; $target = Join-Path $env:LOCALAPPDATA 'CommonGroundsPOS\CommonGroundsPOS.exe'; $work = Join-Path $env:LOCALAPPDATA 'CommonGroundsPOS'; $icon = Join-Path $env:LOCALAPPDATA 'CommonGroundsPOS\CommonGroundsPOS.ico'; $desktop = [Environment]::GetFolderPath('Desktop'); $menu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Common Grounds'; New-Item -Path $menu -ItemType Directory -Force | Out-Null; $s1 = Join-Path $desktop 'CommonGroundsPOS.lnk'; $s2 = Join-Path $menu 'CommonGroundsPOS.lnk'; foreach ($s in @($s1,$s2)) { $lnk = $w.CreateShortcut($s); $lnk.TargetPath = $target; $lnk.WorkingDirectory = $work; if (Test-Path $icon) { $lnk.IconLocation = $icon }; $lnk.Save() }" >>"%LOG_FILE%" 2>&1

echo Installation terminee.
echo Dossier: %TARGET_DIR%
echo Lancement: %TARGET_DIR%\CommonGroundsPOS.exe
start "" "%TARGET_DIR%\CommonGroundsPOS.exe" >nul 2>nul
pause
exit /b 0

