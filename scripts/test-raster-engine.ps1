[CmdletBinding()]
param(
    [string]$Serial,
    [ValidateRange(1, 100)] [int]$Iterations = 10,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ge 7) { $PSNativeCommandUseErrorActionPreference = $false }
$projectRoot = Split-Path -Parent $PSScriptRoot
$appApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$runner = 'com.orbyte.canvasstudio.debug.test/androidx.test.runner.AndroidJUnitRunner'

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $sdkAdb) { return $sdkAdb }
    throw 'No se encontró adb.'
}

$adb = Find-Adb
if (-not $Serial) {
    $device = & $adb devices -l | Where-Object { $_ -match '\sdevice\s+.*model:SM_X700' } | Select-Object -First 1
    if (-not $device) { throw 'No se detectó una Galaxy Tab S8 autorizada.' }
    $Serial = ($device -replace '\s+device\s+.*$', '').Trim()
}

if (-not $SkipBuild) {
    $javaHome = 'C:\Program Files\Android\Android Studio\jbr'
    $env:JAVA_HOME = $javaHome
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:KOTLIN_COMPILER_EXECUTION_STRATEGY = 'in-process'
    Push-Location $projectRoot
    try {
        & .\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación de las pruebas.' }
    } finally { Pop-Location }
}

if (-not $SkipInstall) {
    & $adb -s $Serial install --streaming -r $appApk | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Falló la instalación de la app debug.' }
    & $adb -s $Serial install --streaming -r $testApk | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Falló la instalación del APK de pruebas.' }
}

$started = Get-Date
for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
    $result = & $adb -s $Serial shell am instrument -w -r $runner
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(6 tests\)') {
        $result | Out-Host
        throw "La iteración $iteration falló."
    }
    Write-Host "Iteración $iteration/${Iterations}: OK"
}

$elapsed = (Get-Date) - $started
$strokeChecks = $Iterations * 200
Write-Host "Suite completada: $Iterations iteraciones, $strokeChecks trazos persistidos, $([math]::Round($elapsed.TotalSeconds, 2)) s."
