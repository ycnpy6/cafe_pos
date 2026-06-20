@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0build-clean-installer.ps1" %*
endlocal
