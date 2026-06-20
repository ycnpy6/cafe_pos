@echo off
setlocal
set "APP_DIR=%~dp0"
set "RUNTIME_BIN=%APP_DIR%runtime\bin"
set "JAVA_EXE=%RUNTIME_BIN%\javaw.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=%RUNTIME_BIN%\java.exe"
set "JFX_DIR=%APP_DIR%app\jfx"
set "CP=%APP_DIR%app\cafe-pos-0.1.0.jar;%APP_DIR%app\lib\*"

set "LOG_DIR=%APPDATA%\CafePOS"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
set "LOG_FILE=%LOG_DIR%\launcher.log"

if not exist "%JAVA_EXE%" (
  echo [%date% %time%] Missing Java runtime: "%JAVA_EXE%" >> "%LOG_FILE%"
  exit /b 1
)

if not exist "%JFX_DIR%" (
  echo [%date% %time%] Missing JavaFX folder: "%JFX_DIR%" >> "%LOG_FILE%"
  exit /b 1
)

echo [%date% %time%] Launching: "%JAVA_EXE%" >> "%LOG_FILE%"
start "" "%JAVA_EXE%" -Dprism.order=sw --module-path "%JFX_DIR%" --add-modules javafx.controls,javafx.fxml,javafx.graphics -cp "%CP%" com.cafepos.Launcher
endlocal
