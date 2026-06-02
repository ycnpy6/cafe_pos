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
