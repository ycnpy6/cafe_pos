$ErrorActionPreference = "Stop"

$sourceDir = Join-Path $PSScriptRoot "CommonGroundsPOS"
$installDir = Join-Path $env:LOCALAPPDATA "CommonGroundsPOS"

if (-not (Test-Path $sourceDir)) {
    throw "Dossier source introuvable: $sourceDir"
}

if (Test-Path $installDir) {
    Remove-Item $installDir -Recurse -Force
}

Copy-Item $sourceDir $installDir -Recurse -Force

$exePath = Join-Path $installDir "CommonGroundsPOS.exe"
if (-not (Test-Path $exePath)) {
    throw "Executable introuvable apres copie: $exePath"
}

$iconPath = Join-Path $installDir "CommonGroundsPOS.ico"
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

Write-Host "Installation terminee."
Write-Host "Dossier: $installDir"
Write-Host "Lancement: $exePath"
