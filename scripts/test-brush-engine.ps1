param(
    [string]$Serial = "R52W404GGPK",
    [switch]$Install,
    [switch]$WithoutStress
)

$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root "app\build"
$apk = Join-Path $build "outputs\apk\debug\app-debug.apk"
$test = Join-Path $build "outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$runner = "com.orbyte.canvasstudio.debug.test/androidx.test.runner.AndroidJUnitRunner"
$drawing = "com.orbyte.canvasstudio.drawing"
$model = (& $adb -s $Serial shell getprop ro.product.model | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $model -ne "SM-X700") { throw "No se encontró la Galaxy Tab S8 prevista." }
$android = (& $adb -s $Serial shell getprop ro.build.version.release | Out-String).Trim()
$runId = Get-Date -Format "yyyyMMdd-HHmmss"

function Run-BrushChecks([string]$Name, [string[]]$Classes) {
    $log = Join-Path $build "tab-s8-brush-engine-$runId-$Name.log"
    $argv = @("-s", $Serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", ($Classes -join ","), $runner)
    "Start: $(Get-Date -Format o); $model Android $android" | Set-Content -LiteralPath $log
    "adb $($argv -join ' ')" | Add-Content -LiteralPath $log
    if ($Install) { Get-FileHash $apk,$test | Format-List | Out-String | Add-Content -LiteralPath $log }
    & $adb @argv 2>&1 | Tee-Object -FilePath $log -Append | Select-String 'FAIL|Error|Time:|OK \(|INSTRUMENTATION_CODE'
    $code = $LASTEXITCODE
    "End: $(Get-Date -Format o); ADB exit: $code" | Tee-Object -FilePath $log -Append
    "Registro: $log"
    if ($code -ne 0 -or -not (Select-String -LiteralPath $log -Pattern '^OK \([1-9]\d* tests?\)' -Quiet) -or
        -not (Select-String -LiteralPath $log -Pattern '^INSTRUMENTATION_CODE: -1$' -Quiet)) {
        throw "Falló la tanda $Name."
    }
}

if ($Install) {
    foreach ($file in @($apk, $test)) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Falta compilar $file" }
        & $adb -s $Serial install --no-streaming -r $file
        if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar $file" }
    }
}

try {
    Run-BrushChecks "targeted" @(
        "$drawing.DistanceDabRasterTest",
        "$drawing.brush.WetPigmentHistoryTest",
        "$drawing.brush.BrushPreviewModelTest",
        "$drawing.brush.BrushTestSurfaceTest",
        "$drawing.BrushRasterSessionIntegrationTest",
        "$drawing.raster.DetachedStrokeRasterizerTest",
        "$drawing.ScalableHistoryIntegrationTest"
    )
    if (-not $WithoutStress) {
        Run-BrushChecks "500" @("$drawing.BrushRetentionMatrixTest#professionalDualAndWetBrushesRetainSentinelsAfter500LongStrokes")
    }
} finally {
    & $adb -s $Serial shell am start -W -n "com.orbyte.canvasstudio.debug/com.orbyte.canvasstudio.MainActivity"
}
