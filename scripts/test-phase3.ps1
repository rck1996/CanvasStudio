param(
    [string]$Serial = "",
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$IncludeTenMinute
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $projectRoot "test-logs"
New-Item -ItemType Directory -Force $logs | Out-Null
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }

if (-not (Test-Path $adb)) { throw "ADB no encontrado en $adb" }
if (-not $Serial) {
    $Serial = (& $adb devices | Select-String "\sdevice$").Line.Split("`t")[0] | Select-Object -First 1
}
if (-not $Serial) { throw "No hay una tablet ADB conectada" }

function Invoke-Instrumentation {
    param([string]$Name, [string[]]$Arguments)
    $start = (Get-Date).ToUniversalTime().ToString("o")
    $output = & $adb -s $Serial shell am instrument -w -r @Arguments com.orbyte.canvasstudio.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $adbExit = $LASTEXITCODE
    @(
        "START_UTC=$start"
        "SERIAL=$Serial"
        "MODEL=$(& $adb -s $Serial shell getprop ro.product.model)"
        "ANDROID=$(& $adb -s $Serial shell getprop ro.build.version.release)"
        "ARGUMENTS=$($Arguments -join ' ')"
    ) + $output + @(
        "END_UTC=$((Get-Date).ToUniversalTime().ToString('o'))"
        "ADB_EXIT_CODE=$adbExit"
    ) | Out-File (Join-Path $logs "$Name.log") -Encoding utf8
    if ($adbExit -ne 0 -or $output -match "FAILURES|INSTRUMENTATION_FAILED|Process crashed") {
        throw "Falló $Name. Revisa test-logs/$Name.log"
    }
    $summary = $output | Select-String "OK \([0-9]+ tests?\)|Time:"
    Write-Host "${Name}: $($summary -join ' · ')"
}

Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        $buildStart = (Get-Date).ToUniversalTime().ToString("o")
        & .\gradlew.bat check assembleDebug assembleDebugAndroidTest *> (Join-Path $logs "phase3-gradle.log")
        $buildExit = $LASTEXITCODE
        "START_UTC=$buildStart`nEND_UTC=$((Get-Date).ToUniversalTime().ToString('o'))`nEXIT_CODE=$buildExit" |
            Add-Content (Join-Path $logs "phase3-gradle.log")
        if ($buildExit -ne 0) { throw "Falló Gradle. Revisa test-logs/phase3-gradle.log" }
    }

    if (-not $SkipInstall) {
        & $adb -s $Serial install -r ".\app\build\outputs\apk\debug\app-debug.apk"
        if ($LASTEXITCODE -ne 0) { throw "Falló la instalación de app-debug.apk" }
        & $adb -s $Serial install -r -t ".\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
        if ($LASTEXITCODE -ne 0) { throw "Falló la instalación del APK de instrumentación" }
    }

    $fastClasses = @(
        "com.orbyte.canvasstudio.drawing.ProfessionalBrushFixtureTest"
        "com.orbyte.canvasstudio.drawing.VulkanTileRasterBackendTest"
        "com.orbyte.canvasstudio.ui.tutorial.StudioTutorialStateTest"
    ) -join ","
    Invoke-Instrumentation "phase3-fast" @("-e", "class", $fastClasses)
    Invoke-Instrumentation "phase3-full-without-long-session" @("-e", "notClass", "com.orbyte.canvasstudio.drawing.VulkanStressTest")
    Invoke-Instrumentation "phase3-vulkan-200" @("-e", "class", "com.orbyte.canvasstudio.drawing.VulkanStressTest#vulkanRetainsSentinelsAfter200ThickTileCrossingStrokes")
    Invoke-Instrumentation "phase3-vulkan-500" @("-e", "class", "com.orbyte.canvasstudio.drawing.VulkanStressTest#vulkanCompletes500LongGraphiteStrokesWithUndoRedo")
    if ($IncludeTenMinute) {
        Invoke-Instrumentation "phase3-ten-minute" @("-e", "class", "com.orbyte.canvasstudio.drawing.VulkanStressTest#continuousTenMinuteSessionExercisesBackendSwitchViewHistoryAndSave")
    }
} finally {
    Pop-Location
}
