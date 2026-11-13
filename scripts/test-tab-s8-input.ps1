[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidateNotNullOrEmpty()] [string]$Serial,
    [ValidateSet('Scripted', 'Physical')] [string]$Mode = 'Scripted',
    [switch]$IncludeStress
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if ($Serial -match '^emulator-' -or $Serial -match '^\s|\s$|[\r\n]') {
    throw 'Indica el identificador exacto de la Tab S8 fisica, no un emulador.'
}
if ($IncludeStress -and $Mode -ne 'Scripted') { throw '-IncludeStress solo aplica en modo Scripted.' }

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath -PathType Leaf)) {
    $adbPath = (Get-Command adb -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
}

function Read-Tablet([string[]]$DeviceCommand) {
    $result = & $adbPath -s $Serial @DeviceCommand
    if ($LASTEXITCODE -ne 0) { throw "ADB no pudo ejecutar: $($DeviceCommand -join ' ')." }
    return ($result | Out-String).Trim()
}

$state = Read-Tablet @('get-state')
if ($state -ne 'device') { throw "La Tab S8 no esta autorizada: $state." }
$model = Read-Tablet @('shell', 'getprop', 'ro.product.model')
$emulator = Read-Tablet @('shell', 'getprop', 'ro.kernel.qemu')
if ($model -ne 'SM-X700' -or $emulator -eq '1') { throw "Destino no admitido: $model. Se requiere la Tab S8 SM-X700 fisica." }

$package = 'com.orbyte.canvasstudio.debug'
$testPackage = "$package.test"
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$root = Split-Path -Parent $PSScriptRoot
$report = Join-Path $root ('build/reports/tab-s8-input/{0}-{1}' -f (Get-Date -Format 'yyyyMMdd-HHmmssfff'), $Mode.ToLowerInvariant())
$null = New-Item -ItemType Directory -Path $report
$info = Join-Path $report 'run-info.txt'
$android = Read-Tablet @('shell', 'getprop', 'ro.build.version.release')
$androidBuild = Read-Tablet @('shell', 'getprop', 'ro.build.fingerprint')
@(
    "Start: $(Get-Date -Format o)"
    "Mode: $Mode"
    "Device: $Serial / $model / Android $android"
    "Android build: $androidBuild"
    'Tip-to-screen latency: not measured by ADB or instrumentation.'
) | Set-Content -LiteralPath $info -Encoding UTF8

function Save-InstalledVersion([string]$PackageName) {
    $lines = (Read-Tablet @('shell', 'dumpsys', 'package', $PackageName)) -split '\r?\n'
    $version = $lines | Where-Object { $_ -match '^\s*(versionCode|versionName|lastUpdateTime)=' }
    if (-not ($version -match '^\s*versionCode=')) { throw "No se encontro el paquete instalado: $PackageName." }
    @("Package: $PackageName"; $version) | Add-Content -LiteralPath $info -Encoding UTF8
}

function Run-Instrumentation([string]$Name, [string[]]$Tests, [string[]]$RequiredMethods = @()) {
    $log = Join-Path $report "$Name.log"
    $adbArgs = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', ($Tests -join ','), $runner)
    $displayArgs = $adbArgs | ForEach-Object { if ($_ -match '[\s#]') { '"{0}"' -f $_ } else { $_ } }
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    @("Start: $(Get-Date -Format o)"; "adb $($displayArgs -join ' ')") | Set-Content -LiteralPath $log -Encoding UTF8
    & $adbPath @adbArgs 2>&1 | ForEach-Object { $_.ToString() } |
        Tee-Object -FilePath $log -Append |
        Select-String 'FAIL|Error|Time:|OK \(|INSTRUMENTATION_CODE'
    $code = $LASTEXITCODE
    $clock.Stop()
    "End: $(Get-Date -Format o); ADB exit: $code; elapsedMs: $($clock.ElapsedMilliseconds)" |
        Tee-Object -FilePath $log -Append
    if ($code -ne 0 -or
        -not (Select-String -LiteralPath $log -Pattern '^OK \([1-9]\d* tests?\)\s*$' -Quiet) -or
        -not (Select-String -LiteralPath $log -Pattern '^INSTRUMENTATION_CODE: -1\s*$' -Quiet) -or
        (Select-String -LiteralPath $log -Pattern '^INSTRUMENTATION_STATUS_CODE: -(3|4)\s*$' -Quiet)) {
        throw "Tanda $Name fallida, omitida o incompleta. Consulta $log."
    }
    foreach ($selection in $Tests) {
        $className, $methodName = $selection -split '#', 2
        $classPattern = '^INSTRUMENTATION_STATUS: class=' + [regex]::Escape($className) + '\s*$'
        if (-not (Select-String -LiteralPath $log -Pattern $classPattern -Quiet)) {
            throw "La tanda $Name no ejecuto $className. Verifica la APK de instrumentacion."
        }
        if ($methodName) { $RequiredMethods += $methodName }
    }
    foreach ($methodName in $RequiredMethods) {
        $methodPattern = '^INSTRUMENTATION_STATUS: test=' + [regex]::Escape($methodName) + '\s*$'
        if (-not (Select-String -LiteralPath $log -Pattern $methodPattern -Quiet)) {
            throw "La tanda $Name no ejecuto $methodName. Verifica la APK de instrumentacion."
        }
    }
}

Write-Host "Evidencia: $report"
try {
    Save-InstalledVersion $package
    if ($Mode -eq 'Physical') {
        'condition,trial,camera_fps,contact_frame,first_ink_frame,video_file,notes' |
            Set-Content -LiteralPath (Join-Path $report 'physical-latency.csv') -Encoding UTF8
        'scenario,trial,pass,video_file,notes' |
            Set-Content -LiteralPath (Join-Path $report 'physical-stability.csv') -Encoding UTF8
        Write-Host 'Solo se preparo el registro. Completa la prueba con S Pen segun docs/TAB_S8_INPUT_VALIDATION.md.'
        return
    }

    Save-InstalledVersion $testPackage
    $drawing = 'com.orbyte.canvasstudio.drawing'
    Run-Instrumentation 'input' @(
        "$drawing.input.StylusInputControllerTest",
        "$drawing.input.StrokeInputBatchTest",
        "$drawing.MultiTouchNavigationTest",
        "$drawing.AsyncStrokeCommitIntegrationTest"
    ) @(
        'penReleaseUsesActionPointerEvenWhenItIsNotTheFirstPen',
        'palmReleaseDoesNotHideRemainingPen',
        'longHistoryUsesThePenIndexAndPreservesAllAxesInOrder',
        'canceledPenGestureLeavesCommittedPixelsIntactAndNextStrokeWorks'
    )
    if ($IncludeStress) {
        Run-Instrumentation 'tiles' @("$drawing.SparseTileSurfaceTest")
        Run-Instrumentation '500-strokes' @(
            "$drawing.BrushRetentionMatrixTest#professionalDualAndWetBrushesRetainSentinelsAfter500LongStrokes"
        )
    }
} finally {
    "End: $(Get-Date -Format o)" | Add-Content -LiteralPath $info -Encoding UTF8
}
