param(
    [ValidateSet("exe", "app-image")]
    [string]$Type = "exe"
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $projectRoot

try {
    $mavenCmd = $null
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        $mavenCmd = "mvn"
    } elseif (Test-Path "C:\Program Files\apache-maven-3.9.15\bin\mvn.cmd") {
        $mavenCmd = "C:\Program Files\apache-maven-3.9.15\bin\mvn.cmd"
    }

    if (-not $mavenCmd) {
        throw "Maven introuvable. Installez Maven ou ajoutez-le au PATH."
    }

    if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
        throw "jpackage introuvable. Installez un JDK (17+) incluant jpackage."
    }

    Write-Host "[1/4] Build Maven..."
    & $mavenCmd clean -DskipTests package

    Write-Host "[2/4] Copie des dependances runtime..."
    & $mavenCmd -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/installer-input

    $mainJar = Get-ChildItem "target" -Filter "cafe-pos-*.jar" |
        Where-Object { $_.Name -notmatch "(sources|javadoc|original)" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $mainJar) {
        throw "Jar principal introuvable dans target/."
    }

    Copy-Item $mainJar.FullName "target/installer-input/$($mainJar.Name)" -Force

    $installerDir = Join-Path $projectRoot "target/installer"
    New-Item -Path $installerDir -ItemType Directory -Force | Out-Null

    $version = $mainJar.BaseName.Replace("cafe-pos-", "")

    $jpackageArgs = @(
        "--type", $Type,
        "--name", "CommonGroundsPOS",
        "--dest", "target/installer",
        "--input", "target/installer-input",
        "--main-jar", $mainJar.Name,
        "--main-class", "com.cafepos.MainApp",
        "--app-version", $version,
        "--vendor", "Common Grounds",
        "--description", "Common Grounds Cafe POS",
        "--win-dir-chooser",
        "--win-shortcut",
        "--win-menu",
        "--win-per-user-install"
    )

    if (Test-Path "src/main/resources/com/cafepos/images/logo.ico") {
        $jpackageArgs += @("--icon", "src/main/resources/com/cafepos/images/logo.ico")
    }

    Write-Host "[3/4] Generation package jpackage ($Type)..."
    try {
        & jpackage @jpackageArgs
    } catch {
        if ($Type -eq "exe") {
            Write-Warning "Creation EXE impossible (souvent WiX manquant). Generation app-image en fallback..."
            $fallbackArgs = @(
                "--type", "app-image",
                "--name", "CommonGroundsPOS",
                "--dest", "target/installer",
                "--input", "target/installer-input",
                "--main-jar", $mainJar.Name,
                "--main-class", "com.cafepos.MainApp",
                "--app-version", $version,
                "--vendor", "Common Grounds",
                "--description", "Common Grounds Cafe POS"
            )
            if (Test-Path "src/main/resources/com/cafepos/images/logo.ico") {
                $fallbackArgs += @("--icon", "src/main/resources/com/cafepos/images/logo.ico")
            }
            & jpackage @fallbackArgs
            Write-Warning "Installer EXE non genere. Installez WiX Toolset 3.x puis relancez ce script."
        } else {
            throw
        }
    }

    Write-Host "[4/4] Fichiers generes dans: $installerDir"
    Get-ChildItem $installerDir | Select-Object Name, Length, LastWriteTime
}
finally {
    Pop-Location
}
