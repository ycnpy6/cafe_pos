$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $projectRoot

try {
    $buildScript = Join-Path $PSScriptRoot "build-clean-installer.ps1"
    if (-not (Test-Path $buildScript)) {
        throw "Script introuvable: $buildScript"
    }

    & $buildScript -NoClean

    $appImageSource = Join-Path $projectRoot "dist\app-image\CommonGroundsPOS"
    if (-not (Test-Path $appImageSource)) {
        throw "App-image introuvable: $appImageSource"
    }

    $simpleInstallerDir = Join-Path $projectRoot "dist\simple-installer"
    if (Test-Path $simpleInstallerDir) {
        Remove-Item $simpleInstallerDir -Recurse -Force
    }
    New-Item -Path $simpleInstallerDir -ItemType Directory -Force | Out-Null

    $appImageTarget = Join-Path $simpleInstallerDir "CommonGroundsPOS"
    Copy-Item $appImageSource $appImageTarget -Recurse -Force

    $installPs1 = @'
$ErrorActionPreference = "Stop"

$sourceDir = Join-Path $PSScriptRoot "CommonGroundsPOS"
$installDir = Join-Path $env:LOCALAPPDATA "CommonGroundsPOS"

try {
    if (-not (Test-Path $sourceDir)) {
        throw "Fichiers source introuvables. Extraire d'abord le ZIP complet puis relancer install.bat."
    }

    Get-Process CommonGroundsPOS -ErrorAction SilentlyContinue |
        Stop-Process -Force -ErrorAction SilentlyContinue

    if (Test-Path $installDir) {
        Remove-Item $installDir -Recurse -Force
    }

    Copy-Item $sourceDir $installDir -Recurse -Force

    $exePath = Join-Path $installDir "CommonGroundsPOS.exe"
    if (-not (Test-Path $exePath)) {
        throw "Executable introuvable apres copie: $exePath"
    }

    $iconPath = Join-Path $installDir "CommonGroundsPOS.ico"

    try {
        $desktopDir = [Environment]::GetFolderPath("Desktop")
        $desktopShortcut = Join-Path $desktopDir "CommonGroundsPOS.lnk"

        $startMenuDir = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Common Grounds"
        New-Item -Path $startMenuDir -ItemType Directory -Force | Out-Null
        $startMenuShortcut = Join-Path $startMenuDir "CommonGroundsPOS.lnk"

        $wsh = New-Object -ComObject WScript.Shell

        $desktopLnk = $wsh.CreateShortcut($desktopShortcut)
        $desktopLnk.TargetPath = $exePath
        $desktopLnk.WorkingDirectory = $installDir
        if (Test-Path $iconPath) { $desktopLnk.IconLocation = $iconPath }
        $desktopLnk.Save()

        $startMenuLnk = $wsh.CreateShortcut($startMenuShortcut)
        $startMenuLnk.TargetPath = $exePath
        $startMenuLnk.WorkingDirectory = $installDir
        if (Test-Path $iconPath) { $startMenuLnk.IconLocation = $iconPath }
        $startMenuLnk.Save()
    }
    catch {
        Write-Warning "Raccourcis non crees automatiquement: $($_.Exception.Message)"
    }

    Write-Host "Installation terminee."
    Write-Host "Dossier: $installDir"
    Write-Host "Lancement: $exePath"
    exit 0
}
catch {
    Write-Error $_
    Write-Host ""
    Write-Host "Echec installation."
    Write-Host "Conseil: extraire le ZIP dans un dossier local, puis executer install.bat."
    exit 1
}
'@

    $uninstallPs1 = @'
$ErrorActionPreference = "Stop"

$installDir = Join-Path $env:LOCALAPPDATA "CommonGroundsPOS"
$desktopDir = [Environment]::GetFolderPath("Desktop")
$desktopShortcut = Join-Path $desktopDir "CommonGroundsPOS.lnk"
$startMenuDir = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Common Grounds"
$startMenuShortcut = Join-Path $startMenuDir "CommonGroundsPOS.lnk"

try {
    Get-Process CommonGroundsPOS -ErrorAction SilentlyContinue |
        Stop-Process -Force -ErrorAction SilentlyContinue

    if (Test-Path $desktopShortcut) {
        Remove-Item $desktopShortcut -Force
    }

    if (Test-Path $startMenuShortcut) {
        Remove-Item $startMenuShortcut -Force
    }

    if (Test-Path $startMenuDir) {
        $remaining = Get-ChildItem $startMenuDir -Force -ErrorAction SilentlyContinue
        if (-not $remaining) {
            Remove-Item $startMenuDir -Force
        }
    }

    if (Test-Path $installDir) {
        Remove-Item $installDir -Recurse -Force
    }

    Write-Host "Desinstallation terminee."
    exit 0
}
catch {
    Write-Error $_
    Write-Host "Echec desinstallation."
    exit 1
}
'@

    Set-Content -Path (Join-Path $simpleInstallerDir "install.ps1") -Value $installPs1 -Encoding ascii
    Set-Content -Path (Join-Path $simpleInstallerDir "uninstall.ps1") -Value $uninstallPs1 -Encoding ascii

        $installBat = @'
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
'@

        $uninstallBat = @'
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
'@
    Set-Content -Path (Join-Path $simpleInstallerDir "install.bat") -Value $installBat -Encoding ascii
    Set-Content -Path (Join-Path $simpleInstallerDir "uninstall.bat") -Value $uninstallBat -Encoding ascii

    $zipPath = Join-Path $projectRoot "dist\CommonGroundsPOS-simple-installer.zip"
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }

    Compress-Archive -Path (Join-Path $simpleInstallerDir "*") -DestinationPath $zipPath -Force

    $hash = Get-FileHash $zipPath -Algorithm SHA256
    $hashLine = "$($hash.Hash) *$(Split-Path $zipPath -Leaf)"
    Set-Content -Path ($zipPath + ".sha256") -Value $hashLine -Encoding ascii

    Write-Host "Pack simple genere: $zipPath"
    Write-Host "Checksum: $($zipPath).sha256"
}
finally {
    Pop-Location
}
