[CmdletBinding()]
param(
    [string]$Serial,
    [ValidateRange(1, 100)] [int]$Iterations = 10,
    [ValidateRange(1, 1000)] [int]$MinimumExpectedTests = 40,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$IncludeMassive
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ge 7) { $PSNativeCommandUseErrorActionPreference = $false }
$projectRoot = Split-Path -Parent $PSScriptRoot
$appApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$runner = 'com.orbyte.canvasstudio.debug.test/androidx.test.runner.AndroidJUnitRunner'
$retentionStrokesPerIteration = 807
$reportDirectory = Join-Path $projectRoot 'build\reports\phase8-certification'

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
        & .\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
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
$iterationTimes = [System.Collections.Generic.List[double]]::new()
for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
    $iterationStarted = Get-Date
    $instrumentArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if (-not $IncludeMassive) {
        $instrumentArguments += @(
            '-e',
            'notAnnotation',
            'androidx.test.filters.LargeTest'
        )
    }
    $instrumentArguments += $runner
    $result = & $adb @instrumentArguments
    $resultText = $result -join "`n"
    $match = [regex]::Match($resultText, 'OK \((\d+) tests\)')
    $executedTests = if ($match.Success) { [int]$match.Groups[1].Value } else { 0 }
    if ($LASTEXITCODE -ne 0 -or -not $match.Success -or $executedTests -lt $MinimumExpectedTests) {
        $result | Out-Host
        throw "La iteración $iteration falló o ejecutó menos de $MinimumExpectedTests pruebas."
    }
    $iterationSeconds = ((Get-Date) - $iterationStarted).TotalSeconds
    $iterationTimes.Add($iterationSeconds)
    Write-Host "Iteración $iteration/${Iterations}: OK ($executedTests pruebas, $([math]::Round($iterationSeconds, 2)) s)"
}

$elapsed = (Get-Date) - $started
$strokeChecks = $Iterations * (
    $retentionStrokesPerIteration + $(if ($IncludeMassive) { 500 } else { 0 })
)
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
$report = [ordered]@{
    timestampUtc = (Get-Date).ToUniversalTime().ToString('o')
    deviceSerial = $Serial
    iterations = $Iterations
    testsPerIteration = $executedTests
    totalTestExecutions = $Iterations * $executedTests
    retentionStrokeChecks = $strokeChecks
    elapsedSeconds = [math]::Round($elapsed.TotalSeconds, 2)
    averageIterationSeconds = [math]::Round(($iterationTimes | Measure-Object -Average).Average, 2)
    result = 'PASS'
}
$reportPath = Join-Path $reportDirectory 'latest.json'
$report | ConvertTo-Json | Set-Content -Encoding UTF8 $reportPath
Write-Host "Suite completada: $Iterations iteraciones, $strokeChecks trazos persistidos, $([math]::Round($elapsed.TotalSeconds, 2)) s."
Write-Host "Reporte: $reportPath"
