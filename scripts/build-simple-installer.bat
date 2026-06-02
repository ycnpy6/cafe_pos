@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0build-simple-installer.ps1"
endlocal
