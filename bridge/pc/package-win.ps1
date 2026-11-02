# Builds the Bridge PC program and packs it as a self-contained Windows app (its own Java runtime inside).
# Run from anywhere; paths are resolved from the location of this script:
#   pwsh -File bridge/pc/package-win.ps1        (or: powershell -File bridge/pc/package-win.ps1)
# Needs a JDK 17+ with jpackage on PATH (or JAVA_HOME). Produces:
#   bridge/pc/build/Bridge/          the app-image; Bridge\Bridge.exe is a windowless launcher
#   bridge/pc/build/Bridge-win.zip   the same folder, zipped, made only after the packaged program passed its self-test
# The script verifies the package itself: it starts Bridge.exe --selftest (the launcher is a GUI-subsystem exe, so it must
# be started with Start-Process and waited for; running it straight from a console returns at once and its exit code
# cannot be trusted) and throws unless the exit code is 0 AND the result file ends with "SELFTEST PASSED".

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

# 4. Prove that the packaged program works: run its self-test with the bundled runtime. Bridge.exe has no console, so
#    it reports into a file (--out); the exit code and the last line of that file must both say "passed".
$exe = Join-Path $appImage 'Bridge.exe'
if (-not (Test-Path $exe)) { throw "jpackage did not produce $exe" }
$result = Join-Path ([System.IO.Path]::GetTempPath()) ("bridge-selftest-" + [guid]::NewGuid().ToString('N') + ".txt")
$process = Start-Process -FilePath $exe -ArgumentList @('--selftest', '--out', ('"' + $result + '"')) -PassThru
$null = $process.Handle # keep the handle so that ExitCode is readable after the process has gone
if (-not $process.WaitForExit(120000)) {
    # Each self-test step has its own 30 second limit, so two minutes means the program is stuck.
    try { $process.Kill() } catch { }
    $timedOut = $true
} else {
    $timedOut = $false
}
$lines = @()
if (Test-Path $result) { $lines = @(Get-Content -LiteralPath $result -Encoding UTF8) }
Write-Host '--- Bridge.exe --selftest ---'
if ($lines.Count -gt 0) { $lines | ForEach-Object { Write-Host $_ } } else { Write-Host '(no output file or it is empty)' }
Write-Host '--- end of self-test output ---'
$last = ($lines | Where-Object { $_ -and $_.Trim() } | Select-Object -Last 1)
if ($timedOut) { throw 'The packaged self-test did not finish within 120 seconds.' }
if ($process.ExitCode -ne 0) { throw "The packaged self-test failed: exit code $($process.ExitCode)." }
if ($null -eq $last -or $last.Trim() -ne 'SELFTEST PASSED') { throw "The packaged self-test did not report SELFTEST PASSED (last line: '$last')." }
Remove-Item -LiteralPath $result -Force -ErrorAction SilentlyContinue

# 5. Zip the folder (the archive holds a top-level "Bridge" folder).
Compress-Archive -Path $appImage -DestinationPath $zip -CompressionLevel Optimal

Write-Host "Done: $appImage"
Write-Host "      $zip"
