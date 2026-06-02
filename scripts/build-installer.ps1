param(
    [ValidateSet("exe", "msi", "app-image")]
    [string]$Type = "exe",
    [switch]$NoClean
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

    $mavenArgs = @("-DskipTests")
    if (-not $NoClean) {
        $mavenArgs += "clean"
    }
    $mavenArgs += "package"

    Write-Host "[1/5] Build Maven..."
    & $mavenCmd @mavenArgs

    Write-Host "[2/5] Copie des dependances runtime..."
    & $mavenCmd -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/installer-input

    $mainJar = Get-ChildItem "target" -Filter "cafe-pos-*.jar" |
        Where-Object { $_.Name -notmatch "(sources|javadoc|original)" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $mainJar) {
        throw "Jar principal introuvable dans target/."
    }

    Copy-Item $mainJar.FullName "target/installer-input/$($mainJar.Name)" -Force

    $installerDir = Join-Path $projectRoot "dist/installer"
    if (Test-Path $installerDir) {
        Get-ChildItem $installerDir -Force | Remove-Item -Recurse -Force
    }
    New-Item -Path $installerDir -ItemType Directory -Force | Out-Null

    $version = $mainJar.BaseName.Replace("cafe-pos-", "")
    $generatedType = $Type
    $javaFxModulePath = '$APPDIR\javafx-base-21.0.5-win.jar;$APPDIR\javafx-controls-21.0.5-win.jar;$APPDIR\javafx-fxml-21.0.5-win.jar;$APPDIR\javafx-graphics-21.0.5-win.jar'

    if ($Type -in @("exe", "msi")) {
        $localWixDir = Join-Path $projectRoot ".tools\wix314"
        $hasSystemCandle = Get-Command candle -ErrorAction SilentlyContinue
        $hasSystemLight = Get-Command light -ErrorAction SilentlyContinue
        $hasLocalWix = (Test-Path (Join-Path $localWixDir "candle.exe")) -and
                       (Test-Path (Join-Path $localWixDir "light.exe"))

        if ((-not $hasSystemCandle -or -not $hasSystemLight) -and $hasLocalWix) {
            $env:PATH = "$localWixDir;$env:PATH"
            Write-Host "WiX local detecte: $localWixDir"
        }
    }

    $jpackageArgs = @(
        "--type", $Type,
        "--name", "CommonGroundsPOS",
        "--dest", $installerDir,
        "--input", "target/installer-input",
        "--main-jar", $mainJar.Name,
        "--main-class", "com.cafepos.MainApp",
        "--app-version", $version,
        "--vendor", "Common Grounds",
        "--description", "Common Grounds Cafe POS",
        "--java-options", "--module-path=$javaFxModulePath",
        "--java-options", "--add-modules=javafx.controls,javafx.fxml",
        "--java-options", "-Dprism.order=sw"
    )

    if ($Type -in @("exe", "msi")) {
        $jpackageArgs += @(
            "--win-upgrade-uuid", "4f3769d8-b20b-47aa-a5f3-6e9897666363",
            "--win-dir-chooser",
            "--win-shortcut",
            "--win-menu",
            "--win-per-user-install"
        )
    }

    if (Test-Path "src/main/resources/com/cafepos/images/logo.ico") {
        $jpackageArgs += @("--icon", "src/main/resources/com/cafepos/images/logo.ico")
    }

    Write-Host "[3/5] Generation package jpackage ($Type)..."
    & jpackage @jpackageArgs
    $packageExit = $LASTEXITCODE

    if ($packageExit -ne 0) {
        if ($Type -eq "exe") {
            Write-Warning "Creation EXE impossible. Tentative MSI puis app-image en fallback..."

            $msiArgs = @(
                "--type", "msi",
                "--name", "CommonGroundsPOS",
                "--dest", $installerDir,
                "--input", "target/installer-input",
                "--main-jar", $mainJar.Name,
                "--main-class", "com.cafepos.MainApp",
                "--app-version", $version,
                "--vendor", "Common Grounds",
                "--description", "Common Grounds Cafe POS",
                "--java-options", "--module-path=$javaFxModulePath",
                "--java-options", "--add-modules=javafx.controls,javafx.fxml",
                "--java-options", "-Dprism.order=sw",
                "--win-upgrade-uuid", "4f3769d8-b20b-47aa-a5f3-6e9897666363",
                "--win-dir-chooser",
                "--win-shortcut",
                "--win-menu",
                "--win-per-user-install"
            )
            if (Test-Path "src/main/resources/com/cafepos/images/logo.ico") {
                $msiArgs += @("--icon", "src/main/resources/com/cafepos/images/logo.ico")
            }

            & jpackage @msiArgs
            if ($LASTEXITCODE -eq 0) {
                Write-Warning "EXE non genere, MSI genere avec succes."
                $generatedType = "msi"
                $packageExit = 0
            }
        }

        if ($packageExit -ne 0) {
            Write-Warning "Generation app-image en fallback..."
            $appImageDir = Join-Path $installerDir "CommonGroundsPOS"
            if (Test-Path $appImageDir) {
                Remove-Item $appImageDir -Recurse -Force
            }

            $fallbackArgs = @(
                "--type", "app-image",
                "--name", "CommonGroundsPOS",
                "--dest", $installerDir,
                "--input", "target/installer-input",
                "--main-jar", $mainJar.Name,
                "--main-class", "com.cafepos.MainApp",
                "--app-version", $version,
                "--vendor", "Common Grounds",
                "--description", "Common Grounds Cafe POS",
                "--java-options", "--module-path=$javaFxModulePath",
                "--java-options", "--add-modules=javafx.controls,javafx.fxml",
                "--java-options", "-Dprism.order=sw"
            )
            if (Test-Path "src/main/resources/com/cafepos/images/logo.ico") {
                $fallbackArgs += @("--icon", "src/main/resources/com/cafepos/images/logo.ico")
            }

            & jpackage @fallbackArgs
            if ($LASTEXITCODE -ne 0) {
                throw "Echec jpackage principal et fallback app-image."
            }
            $generatedType = "app-image"
            $packageExit = 0
            if ($Type -in @("exe", "msi")) {
                Write-Warning "Installer Windows non genere. Fallback app-image cree."
            }
        }
    }

    Write-Host "[4/5] Generation checksums SHA256..."
    Get-ChildItem $installerDir -File | ForEach-Object {
        $hash = Get-FileHash $_.FullName -Algorithm SHA256
        $line = "$($hash.Hash) *$($_.Name)"
        Set-Content -Path ($_.FullName + ".sha256") -Value $line -Encoding ascii
    }

    Write-Host "[5/5] Fichiers generes dans: $installerDir (type final: $generatedType)"
    Get-ChildItem $installerDir | Select-Object Name, Length, LastWriteTime
}
finally {
    Pop-Location
}
