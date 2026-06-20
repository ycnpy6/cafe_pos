param(
    [switch]$NoClean,
    [switch]$SkipInnoSetup
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $projectRoot

try {
    $mavenCmd = $null
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        $mavenCmd = "mvn"
    } elseif (Test-Path (Join-Path $projectRoot ".tools\apache-maven-3.9.11\bin\mvn.cmd")) {
        $mavenCmd = (Join-Path $projectRoot ".tools\apache-maven-3.9.11\bin\mvn.cmd")
    } elseif (Test-Path "C:\Program Files\apache-maven-3.9.15\bin\mvn.cmd") {
        $mavenCmd = "C:\Program Files\apache-maven-3.9.15\bin\mvn.cmd"
    }

    if (-not $mavenCmd) {
        throw "Maven introuvable. Installez Maven ou ajoutez-le au PATH."
    }

    $jpackageExe = $null
    $jpackageCmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($jpackageCmd) {
        $jpackageExe = $jpackageCmd.Source
    } elseif ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
        if (Test-Path $candidate) {
            $jpackageExe = $candidate
        }
    }

    if (-not $jpackageExe) {
        throw "jpackage introuvable. Installez un JDK (17+) incluant jpackage."
    }

    $isccExe = $null
    if (-not $SkipInnoSetup) {
        $isccCmd = Get-Command ISCC -ErrorAction SilentlyContinue
        if ($isccCmd) {
            $isccExe = $isccCmd.Source
        } elseif (Test-Path "C:\Program Files (x86)\Inno Setup 6\ISCC.exe") {
            $isccExe = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
        } elseif (Test-Path "C:\Program Files\Inno Setup 6\ISCC.exe") {
            $isccExe = "C:\Program Files\Inno Setup 6\ISCC.exe"
        }

        if (-not $isccExe) {
            throw "Inno Setup (ISCC) introuvable. Installez Inno Setup et ajoutez ISCC.exe au PATH, ou utilisez -SkipInnoSetup pour ne generer que l'app-image."
        }
    }

    $mavenArgs = @("-DskipTests")
    if (-not $NoClean) {
        $mavenArgs += "clean"
    }
    $mavenArgs += "package"

    Write-Host "[1/5] Build Maven..."
    & $mavenCmd @mavenArgs

    Write-Host "[2/5] Copie des dependances runtime..."
    $appInput = Join-Path $projectRoot "target\app-input"
    if (Test-Path $appInput) {
        Remove-Item $appInput -Recurse -Force
    }
    New-Item -Path (Join-Path $appInput "lib") -ItemType Directory -Force | Out-Null
    $depOutput = Join-Path $appInput "lib"
    & $mavenCmd -DskipTests dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=$depOutput" "-DexcludeGroupIds=org.openjfx"

    $mainJar = Get-ChildItem "target" -Filter "cafe-pos-*.jar" |
        Where-Object { $_.Name -notmatch "(sources|javadoc|original)" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $mainJar) {
        throw "Jar principal introuvable dans target/."
    }

    Copy-Item $mainJar.FullName (Join-Path $appInput $mainJar.Name) -Force

    $appImageRoot = Join-Path $projectRoot "dist\app-image"
    if (Test-Path $appImageRoot) {
        Remove-Item $appImageRoot -Recurse -Force
    }
    New-Item -Path $appImageRoot -ItemType Directory -Force | Out-Null

    $installerDir = Join-Path $projectRoot "dist\installer"
    if (Test-Path $installerDir) {
        Get-ChildItem $installerDir -Force | Remove-Item -Recurse -Force
    }
    New-Item -Path $installerDir -ItemType Directory -Force | Out-Null

    $version = $mainJar.BaseName.Replace("cafe-pos-", "")
    $javaFxVersion = "21.0.5"
    $m2 = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx"
    $javaFxJars = @(
        (Join-Path $m2 "javafx-base\$javaFxVersion\javafx-base-$javaFxVersion-win.jar"),
        (Join-Path $m2 "javafx-controls\$javaFxVersion\javafx-controls-$javaFxVersion-win.jar"),
        (Join-Path $m2 "javafx-fxml\$javaFxVersion\javafx-fxml-$javaFxVersion-win.jar"),
        (Join-Path $m2 "javafx-graphics\$javaFxVersion\javafx-graphics-$javaFxVersion-win.jar")
    )

    foreach ($jarPath in $javaFxJars) {
        if (-not (Test-Path $jarPath)) {
            throw "JavaFX jar introuvable: $jarPath"
        }
    }

    $javaFxModulePath = ($javaFxJars -join ";")

    $iconPath = "src\main\resources\com\cafepos\images\icon.ico"
    $jpackageArgs = @(
        "--type", "app-image",
        "--name", "CommonGroundsPOS",
        "--dest", $appImageRoot,
        "--input", $appInput,
        "--main-jar", $mainJar.Name,
        "--main-class", "com.cafepos.Launcher",
        "--app-version", $version,
        "--vendor", "Common Grounds",
        "--description", "Common Grounds Cafe POS",
        "--module-path", $javaFxModulePath,
        "--add-modules", "javafx.controls,javafx.fxml,java.naming,java.sql,java.desktop,java.logging,java.management,java.xml,jdk.crypto.ec,jdk.unsupported,java.scripting,java.net.http,java.prefs",
        "--java-options", "-Dprism.order=sw"
    )

    if (Test-Path $iconPath) {
        $jpackageArgs += @("--icon", $iconPath)
    }

    Write-Host "[3/5] Generation app-image jpackage..."
    & $jpackageExe @jpackageArgs

    $appImagePath = Join-Path $appImageRoot "CommonGroundsPOS"
    if (Test-Path $iconPath) {
        Copy-Item $iconPath (Join-Path $appImagePath "CommonGroundsPOS.ico") -Force
    }
    $jfxDir = Join-Path $appImagePath "app\jfx"
    if (Test-Path $jfxDir) {
        Remove-Item $jfxDir -Recurse -Force
    }
    New-Item -Path $jfxDir -ItemType Directory -Force | Out-Null
    foreach ($jarPath in $javaFxJars) {
        Copy-Item $jarPath $jfxDir -Force
    }

    $cfgPath = Join-Path $appImagePath "app\CommonGroundsPOS.cfg"
    if (Test-Path $cfgPath) {
        $cfgLines = Get-Content -LiteralPath $cfgPath
        $modulePathLine = 'java-options=--module-path=$APPDIR\jfx'
        $addModulesLine = "java-options=--add-modules=javafx.controls,javafx.fxml"
        if ($cfgLines -notcontains $modulePathLine) {
            $cfgLines += $modulePathLine
        }
        if ($cfgLines -notcontains $addModulesLine) {
            $cfgLines += $addModulesLine
        }
        Set-Content -LiteralPath $cfgPath -Value $cfgLines -Encoding ascii
    }

    if ($env:JAVA_HOME) {
        $javaBin = Join-Path $env:JAVA_HOME "bin"
        $runtimeBin = Join-Path $appImagePath "runtime\bin"
        if (Test-Path $runtimeBin) {
            if (Test-Path (Join-Path $javaBin "java.exe")) {
                Copy-Item (Join-Path $javaBin "java.exe") $runtimeBin -Force
            }
            if (Test-Path (Join-Path $javaBin "javaw.exe")) {
                Copy-Item (Join-Path $javaBin "javaw.exe") $runtimeBin -Force
            }
        }
    }

    if ($SkipInnoSetup) {
        Write-Host "[4/5] Inno Setup ignore (--SkipInnoSetup)."
        Write-Host "[5/5] App-image genere dans: $appImageRoot"
        Get-ChildItem $appImageRoot | Select-Object Name, Length, LastWriteTime
    } else {
        Write-Host "[4/5] Build Inno Setup..."
        & $isccExe "installer\setup.iss"

        Write-Host "[5/5] Fichiers generes dans: $installerDir"
        Get-ChildItem $installerDir | Select-Object Name, Length, LastWriteTime
    }
}
finally {
    Pop-Location
}
