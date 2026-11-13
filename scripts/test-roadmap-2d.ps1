[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [switch]$Install,
    [switch]$IncludeStress,
    [switch]$Full,
    [switch]$FullOnly
)

$ErrorActionPreference = 'Stop'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root 'app/build'
$apk = Join-Path $build 'outputs/apk/debug/app-debug.apk'
$testApk = Join-Path $build 'outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$model = (& $adb -s $Serial shell getprop ro.product.model | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $model -ne 'SM-X700') { throw 'No se encontró la Galaxy Tab S8 indicada.' }
$android = (& $adb -s $Serial shell getprop ro.build.version.release | Out-String).Trim()
$stamp = Get-Date -Format yyyyMMdd-HHmmss
$package = 'com.orbyte.canvasstudio.debug'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$drawing = 'com.orbyte.canvasstudio.drawing'

if ($Install) {
    foreach ($file in @($apk, $testApk)) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Falta compilar $file" }
        & $adb -s $Serial install --no-streaming -r $file
        if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar $file sin borrar sus datos." }
    }
}

function Invoke-Suite([string]$Name, [string]$Filter, [string[]]$Selections) {
    $log = Join-Path $build "tab-s8-roadmap-$stamp-$Name.log"
    $argsForAdb = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', $Filter, ($Selections -join ','), $runner)
    @("Start: $(Get-Date -Format o); $model Android $android"; "adb $($argsForAdb -join ' ')") |
        Set-Content -LiteralPath $log
    if ($Install) { Get-FileHash $apk,$testApk | Format-List | Out-String | Add-Content -LiteralPath $log }
    & $adb @argsForAdb 2>&1 | Tee-Object -FilePath $log -Append |
        Select-String 'FAIL|Error in|Tests run:|Time:|OK \(|INSTRUMENTATION_CODE'
    $code = $LASTEXITCODE
    "End: $(Get-Date -Format o); ADB exit: $code; $log" | Tee-Object -FilePath $log -Append
    if ($code -ne 0 -or -not (Select-String -LiteralPath $log -Pattern '^OK \([1-9]\d* tests?\)' -Quiet) -or
        -not (Select-String -LiteralPath $log -Pattern '^INSTRUMENTATION_CODE: -1$' -Quiet)) {
        throw "Falló $Name. Registro: $log"
    }
    if ($Filter -eq 'class') {
        foreach ($selection in $Selections) {
            $class, $method = $selection -split '#', 2
            if (-not (Select-String -LiteralPath $log -Pattern ("^INSTRUMENTATION_STATUS: class=" + [regex]::Escape($class) + "$") -Quiet)) {
                throw "No se ejecutó $class. Revisa la APK de instrumentación."
            }
            if ($method -and -not (Select-String -LiteralPath $log -Pattern ("^INSTRUMENTATION_STATUS: test=" + [regex]::Escape($method) + "$") -Quiet)) {
                throw "No se ejecutó $method. Revisa la APK de instrumentación."
            }
        }
    }
}

try {
    if (-not $FullOnly) {
        Invoke-Suite 'focused' 'class' @(
            "$drawing.input.StylusInputControllerTest", "$drawing.input.StrokeInputBatchTest",
            "$drawing.MultiTouchNavigationTest", "$drawing.AsyncStrokeCommitIntegrationTest",
            "$drawing.BrushRasterSessionIntegrationTest", "$drawing.brush.BrushPreviewModelTest",
            "$drawing.BrushPhase6Test", "$drawing.pipeline.StrokePreviewRendererTest",
            "$drawing.SparseTileSurfaceTest#translucentTilesDoNotDoubleBlendAtFractionalZoom",
            "$drawing.SparseTileSurfaceTest#hardwareViewportKeepsTranslucentTilesUniformAtZoomAndRotation",
            "$drawing.BrushRetentionMatrixTest#authoredBrushPixelsSurviveCrossTileUndoAndRedo",
            "$drawing.ProfessionalBrushVisualMatrixTest",
            "$drawing.ProfessionalBrushProfilesTest",
            "$drawing.brush.BrushTestSurfaceTest", "$drawing.DistanceDabRasterTest", "$drawing.brush.WetPigmentHistoryTest",
            "$drawing.QuickShapeIntegrationTest", "$drawing.MaskEditingModeTest", "$drawing.ScalableHistoryIntegrationTest",
            "$drawing.PsdLayerReaderTest", "$drawing.BasicPsdCodecTest", "$drawing.raster.LayeredPsdExchangeTest",
            "$drawing.raster.DocumentExportSnapshotTest", "$drawing.LargeCanvasExportTest",
            'com.orbyte.canvasstudio.model.ProjectRepositoryRecoveryTest',
            'com.orbyte.canvasstudio.ui.screens.GalleryTrashUiTest',
            'com.orbyte.canvasstudio.ui.workspace.QuickAccessTest',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#captureGallery',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#mountainEditorDoesNotShowWhiteTileBoundariesAtFitZoom',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#updatedBrushesAreSelectableAndLeaveVisibleStrokesInEditor',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#wheelOffersEditingWithoutHoldingASlot',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#guidesAndExportsHaveOneHomeWithoutRemovingTools',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#clippingNamesItsBaseAndExplainsUnsupportedExport',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#brushPanelKeepsSearchAndKeepsTestPaperAcrossTabs',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#singleShapeGroupOffersFourEditableShapesWithoutDuplicateActions',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#secondaryToolsRemainSelectableFromTheRail',
            'com.orbyte.canvasstudio.ui.screens.DesignScreenshotTest#maskControlsSwitchToolsAndLeaveMaskEditing'
        )
    }
    if ($IncludeStress) {
        Invoke-Suite '500' 'class' @("$drawing.BrushRetentionMatrixTest#professionalDualAndWetBrushesRetainSentinelsAfter500LongStrokes")
        Invoke-Suite '200' 'class' @("$drawing.SparseTileSurfaceTest#twoHundredThickStrokesSurviveFlushEvictionAndReload")
    }
    if ($Full -or $FullOnly) {
        Invoke-Suite 'full' 'notClass' @("$drawing.VulkanEnduranceTest")
    }
} finally {
    & $adb -s $Serial shell am start -W -n "$package/com.orbyte.canvasstudio.MainActivity"
}
