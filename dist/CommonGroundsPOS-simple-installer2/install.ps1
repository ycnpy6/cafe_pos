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
