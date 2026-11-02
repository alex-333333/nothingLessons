# Builds the Bridge PC program and packs it as a self-contained Windows app (its own Java runtime inside).
# Run from anywhere; paths are resolved from the location of this script:
#   pwsh -File bridge/pc/package-win.ps1        (or: powershell -File bridge/pc/package-win.ps1)
# Needs a JDK 17+ with jpackage on PATH (or JAVA_HOME). Produces:
#   bridge/pc/build/Bridge/          the app-image; Bridge\Bridge.exe is a windowless launcher
#   bridge/pc/build/Bridge-win.zip   the same folder, zipped
# Check the result with:  Bridge\Bridge.exe --selftest --out result.txt   (exit code 0 = ok; the file says what happened)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) # this script is bridge/pc/package-win.ps1
$buildDir = Join-Path $repoRoot 'bridge/pc/build'
$libDir   = Join-Path $buildDir 'install/pc/lib'
$appImage = Join-Path $buildDir 'Bridge'
$zip      = Join-Path $buildDir 'Bridge-win.zip'

function Invoke-Native([string]$What, [scriptblock]$Command) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$What failed with exit code $LASTEXITCODE" }
}

# 1. Build the jars (pc.jar plus every dependency) into bridge/pc/build/install/pc/lib.
$onWindows = ($env:OS -eq 'Windows_NT')
$gradlew = if ($onWindows) { Join-Path $repoRoot 'gradlew.bat' } else { Join-Path $repoRoot 'gradlew' }
Push-Location $repoRoot
try {
    Invoke-Native 'gradlew :pc:installDist' { & $gradlew -p bridge ':pc:installDist' }
} finally {
    Pop-Location
}
if (-not (Test-Path (Join-Path $libDir 'pc.jar'))) { throw "pc.jar not found in $libDir" }

# 2. The JRE modules the program needs. Modules this JDK does not have (jdk.crypto.ec was merged into java.base
#    in JDK 22) are left out, because jpackage rejects unknown module names.
$wanted = @('java.base', 'java.desktop', 'java.logging', 'java.naming', 'java.sql', 'java.management',
            'jdk.unsupported', 'jdk.crypto.ec', 'java.security.jgss')
$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java' } else { 'java' }
$ErrorActionPreference = 'Continue' # the JVM may print "Picked up JAVA_TOOL_OPTIONS" on stderr
$available = @(& $javaExe --list-modules 2>$null | ForEach-Object { ($_ -split '@')[0].Trim() })
$ErrorActionPreference = 'Stop'
$modules = @($wanted | Where-Object { $available -contains $_ })
if ($modules.Count -ne $wanted.Count) {
    Write-Host ('Skipping modules this JDK does not have: ' + (($wanted | Where-Object { $modules -notcontains $_ }) -join ', '))
}

# 3. jpackage refuses to write into an existing folder, so remove the previous result first.
if (Test-Path $appImage) { Remove-Item -Recurse -Force $appImage }
if (Test-Path $zip) { Remove-Item -Force $zip }

$jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/jpackage' } else { 'jpackage' }
Invoke-Native 'jpackage' {
    & $jpackage --type app-image --name Bridge --input $libDir --main-jar pc.jar `
        --main-class io.github.alex333333.bridge.pc.MainKt --dest $buildDir `
        --add-modules ($modules -join ',')
}

# 4. Zip the folder (the archive holds a top-level "Bridge" folder).
Compress-Archive -Path $appImage -DestinationPath $zip -CompressionLevel Optimal

Write-Host "Done: $appImage"
Write-Host "      $zip"
