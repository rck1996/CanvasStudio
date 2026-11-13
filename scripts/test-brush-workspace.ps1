param(
    [string]$Serial = "R52W404GGPK",
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root "app\build"
$appApk = Join-Path $build "outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $build "outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$package = "com.orbyte.canvasstudio.debug"
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$design = "com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest"
$drawing = "com.orbyte.canvasstudio.drawing"

function Read-Device([string[]]$Command) {
    $lines = & $adb -s $Serial shell @Command
    if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar el dispositivo: $($Command -join ' ')" }
    return ($lines -join "`n").Trim()
}

$model = Read-Device @("getprop", "ro.product.model")
$android = Read-Device @("getprop", "ro.build.version.release")
$rotation = Read-Device @("cmd", "window", "user-rotation")
$timeout = Read-Device @("settings", "get", "system", "screen_off_timeout")
if ($model -ne "SM-X700") { throw "Este script espera una Galaxy Tab S8 (SM-X700), no $model." }
if ($rotation -notmatch '^(free|lock [0-3])$' -or $timeout -notmatch '^\d+$') {
    throw "No se pudo guardar la configuración original de pantalla."
}

function Run-Suite([string]$Orientation, [string[]]$Tests) {
    $log = Join-Path $build "tab-s8-brush-workspace-verified-$Orientation.log"
    $joined = $Tests -join ","
    $argv = @("-s", $Serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", $joined, $runner)
    "Start: $(Get-Date -Format o); $model Android $android" | Set-Content -LiteralPath $log
    "adb $($argv -join ' ')" | Add-Content -LiteralPath $log
    Read-Device @("dumpsys", "package", $package) | Select-String 'version(?:Code|Name)=[^\s]+' -AllMatches |
        ForEach-Object { $_.Matches.Value -join " " } | Add-Content -LiteralPath $log
    if ($Install) { Get-FileHash $appApk,$testApk | Format-List | Out-String | Add-Content -LiteralPath $log }
    & $adb @argv 2>&1 | Tee-Object -FilePath $log -Append | Select-String 'FAIL|Error|Time:|OK \(|INSTRUMENTATION_CODE'
    $result = $LASTEXITCODE
    "End: $(Get-Date -Format o); ADB exit: $result" | Tee-Object -FilePath $log -Append
    if ($result -ne 0 -or -not (Select-String $log -Pattern '^OK \([1-9]\d* tests?\)' -Quiet) -or
        -not (Select-String $log -Pattern '^INSTRUMENTATION_CODE: -1$' -Quiet)) {
        throw "Falló la tanda $Orientation. Registro: $log"
    }
}

function Save-Capture([string]$Orientation) {
    $remote = "/sdcard/Pictures/CanvasStudioCaptures"
    $entry = Read-Device @("ls", "-1t", $remote)
    $name = $entry -split "`n" | Where-Object { $_.Trim() -like "03-paleta-pinceles*.png" } | Select-Object -First 1
    if ($name) {
        & $adb -s $Serial pull "$remote/$($name.Trim())" (Join-Path $build "tab-s8-pinceles-$Orientation.png")
    }
}

if ($Install) {
    foreach ($apk in @($appApk, $testApk)) {
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Falta compilar $apk" }
        & $adb -s $Serial install --no-streaming -r $apk
        if ($LASTEXITCODE -ne 0) { throw "No se instaló $apk" }
    }
}

try {
    & $adb -s $Serial shell settings put system screen_off_timeout 600000
    if ($LASTEXITCODE -ne 0) { throw "No se pudo mantener activa la pantalla." }
    & $adb -s $Serial shell input keyevent KEYCODE_WAKEUP
    & $adb -s $Serial shell cmd statusbar collapse
    if ((Read-Device @("dumpsys", "window", "policy")) -match "mInputRestricted=true") {
        throw "Desbloquea la tablet para ejecutar las pruebas de interfaz."
    }
    & $adb -s $Serial shell cmd window user-rotation lock 1
    Run-Suite "horizontal" @(
        $design,
        "$drawing.brush.BrushTestSurfaceTest",
        "$drawing.brush.BrushPreviewModelTest",
        "$drawing.ProfessionalBrushLibraryVisualTest",
        "com.orbyte.canvasstudio.ui.screens.QuickDialUiTest",
        "$drawing.MaskEditingModeTest"
    )
    Save-Capture "horizontal"
    & $adb -s $Serial pull "/sdcard/Android/data/$package/files/brush-certification/blind-comparison.png" (Join-Path $build "tab-s8-brush-blind-comparison.png")
    & $adb -s $Serial shell cmd window user-rotation lock 0
    Run-Suite "vertical" @(
        "$design#captureBrushWorkspace",
        "$design#brushPanelKeepsSearchAndKeepsTestPaperAcrossTabs",
        "$design#editorStartsWithMoreCanvasAndBrushControlsOutsideIt"
    )
    Save-Capture "vertical"
} finally {
    & $adb -s $Serial shell settings put system screen_off_timeout $timeout
    if ($rotation -eq "free") { & $adb -s $Serial shell cmd window user-rotation free }
    else { & $adb -s $Serial shell cmd window user-rotation lock $rotation.Substring(5) }
    & $adb -s $Serial shell am start -W -n "$package/com.orbyte.canvasstudio.MainActivity"
}
