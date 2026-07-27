[CmdletBinding()]
param(
    [string]$Serial,
    [ValidateRange(1, 200)] [int]$StrokeCount = 40,
    [ValidateRange(300, 1500)] [int]$StrokeDurationMs = 500,
    [string[]]$BrushPresets = @(),
    [ValidateRange(0, 20)] [int]$BrushSizeIncrements = 0,
    [int]$ProjectTapX = 1500,
    [int]$ProjectTapY = 600,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
# `adb shell monkey` writes normal diagnostics to stderr. In PowerShell 7 those are promoted
# by ErrorActionPreference unless this compatibility switch is disabled; Invoke-Adb checks the
# actual process exit code below instead.
if ($PSVersionTable.PSVersion.Major -ge 7) { $PSNativeCommandUseErrorActionPreference = $false }
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageName = 'com.orbyte.canvasstudio.debug'
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$reportDirectory = Join-Path $projectRoot 'build\reports\tablet-stress'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    $sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $sdkAdb) { return $sdkAdb }
    throw 'No se encontró adb. Instala Android Platform Tools o agrega adb al PATH.'
}

$adb = Find-Adb

function Invoke-Adb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $stdoutFile = Join-Path ([System.IO.Path]::GetTempPath()) "canvasstudio-adb-$PID-$([guid]::NewGuid()).out"
    $stderrFile = Join-Path ([System.IO.Path]::GetTempPath()) "canvasstudio-adb-$PID-$([guid]::NewGuid()).log"
    try {
        $quotedArguments = (@('-s', $Serial) + $Arguments | ForEach-Object {
            '"' + ($_ -replace '"', '\"') + '"'
        }) -join ' '
        $process = Start-Process -FilePath $adb -ArgumentList $quotedArguments -Wait -PassThru -NoNewWindow `
            -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile
        $result = if (Test-Path $stdoutFile) { Get-Content -Raw $stdoutFile } else { '' }
        $exitCode = $process.ExitCode
        $stderr = if (Test-Path $stderrFile) { Get-Content -Raw $stderrFile } else { '' }
        if ($exitCode -ne 0) {
            throw "ADB falló: $($Arguments -join ' ')`n$result`n$stderr"
        }
        return ((@($result) + $stderr) | Out-String).Trim()
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $stdoutFile
        Remove-Item -Force -ErrorAction SilentlyContinue $stderrFile
    }
}

function Get-ConnectedTablet {
    $devices = & $adb devices -l
    $candidate = $devices |
        Where-Object { $_ -match '\sdevice\s+.*model:SM_X700' } |
        Select-Object -First 1
    if (-not $candidate) {
        throw 'No se detectó una Galaxy Tab S8 (SM-X700) autorizada por ADB inalámbrico.'
    }
    # mDNS wireless serials can contain spaces, e.g. "... (2)._adb-tls-connect...".
    return ($candidate -replace '\s+device\s+.*$', '').Trim()
}

function Get-WindowXml {
    $remotePath = '/sdcard/canvasstudio-test-window.xml'
    # Samsung occasionally rejects a dump while Compose is settling after an
    # activity transition. Retry the read-only capture instead of failing a run.
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        try {
            Invoke-Adb @('shell', 'uiautomator', 'dump', $remotePath) | Out-Null
            return Invoke-Adb @('exec-out', 'cat', $remotePath)
        } catch {
            if ($attempt -eq 2) { throw }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Open-TestProject {
    $window = Get-WindowXml
    if ($window -match 'content-desc="Deshacer"') { return $true }
    # Fresh installs show a one-time onboarding dialog before the gallery.  Dismiss it
    # so this stress runner can be used on a clean tablet without manual preparation.
    if ($window -match 'Bienvenido a Canvas Studio') {
        Invoke-Adb @('shell', 'input', 'tap', 1735, 1110) | Out-Null
        Start-Sleep -Seconds 2
        $window = Get-WindowXml
        if ($window -match 'content-desc="Deshacer"') { return $true }
    }
    if ($window -notmatch 'Mis proyectos') { return $false }
    Invoke-Adb @('shell', 'input', 'tap', $ProjectTapX, $ProjectTapY) | Out-Null
    Start-Sleep -Seconds 6
    $window = Get-WindowXml
    return ($window -match 'content-desc="Deshacer"')
}

function Save-Screenshot {
    param([Parameter(Mandatory)][string]$Name)
    $remotePath = "/sdcard/canvasstudio-$Name.png"
    $localPath = Join-Path $reportDirectory "$timestamp-$Name.png"
    Invoke-Adb @('shell', 'screencap', '-p', $remotePath) | Out-Null
    Invoke-Adb @('pull', $remotePath, $localPath) | Out-Null
    Invoke-Adb @('shell', 'rm', '-f', $remotePath) | Out-Null
    return $localPath
}

function Test-UniqueStrokeVisibility {
    param(
        [Parameter(Mandatory)][string]$BaselinePath,
        [Parameter(Mandatory)][string]$CandidatePath,
        [Parameter(Mandatory)][object[]]$Strokes,
        [Parameter(Mandatory)][int]$ExpectedCount,
        [ValidateRange(0.5, 1.0)][double]$InputToCaptureScale = 1.0
    )

    Add-Type -AssemblyName System.Drawing
    $baseline = [System.Drawing.Bitmap]::FromFile($BaselinePath)
    $candidate = [System.Drawing.Bitmap]::FromFile($CandidatePath)
    try {
        if ($baseline.Width -ne $candidate.Width -or $baseline.Height -ne $candidate.Height) {
            throw 'Las capturas no tienen las mismas dimensiones; no se puede verificar la matriz de trazos.'
        }
        $visible = 0
        $missing = [System.Collections.Generic.List[int]]::new()
        for ($index = 0; $index -lt $ExpectedCount; $index++) {
            $stroke = $Strokes[$index]
            $changedPixels = 0
            # Modern Samsung screencap and adb input use the same physical display coordinates.
            $left = [Math]::Max(0, [Math]::Round($stroke[0] * $InputToCaptureScale) - 5)
            $top = [Math]::Max(0, [Math]::Round($stroke[1] * $InputToCaptureScale) - 5)
            $right = [Math]::Min($baseline.Width - 1, [Math]::Round($stroke[2] * $InputToCaptureScale) + 5)
            $bottom = [Math]::Min($baseline.Height - 1, [Math]::Round($stroke[3] * $InputToCaptureScale) + 5)
            for ($y = $top; $y -le $bottom -and $changedPixels -lt 3; $y += 2) {
                for ($x = $left; $x -le $right -and $changedPixels -lt 3; $x += 2) {
                    $before = $baseline.GetPixel($x, $y)
                    $after = $candidate.GetPixel($x, $y)
                    $delta = [Math]::Abs($before.R - $after.R) + [Math]::Abs($before.G - $after.G) + [Math]::Abs($before.B - $after.B)
                    if ($delta -ge 40) { $changedPixels++ }
                }
            }
            if ($changedPixels -ge 3) { $visible++ } else { $missing.Add($index + 1) }
        }
        return [pscustomobject]@{ visible = $visible; missing = @($missing); expected = $ExpectedCount }
    } finally {
        $baseline.Dispose()
        $candidate.Dispose()
    }
}

function Get-TextBounds {
    param(
        [Parameter(Mandatory)][string]$WindowXml,
        [Parameter(Mandatory)][string]$Text
    )
    $pattern = '<node[^>]*text="' + [regex]::Escape($Text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    return [regex]::Match($WindowXml, $pattern)
}

function Select-BrushPreset {
    param([Parameter(Mandatory)][string]$Name)
    $window = Get-WindowXml
    $brushesTab = Get-TextBounds $window 'Pinceles'
    if (-not $brushesTab.Success) { throw 'No se encontró la pestaña Pinceles en el editor.' }
    $tabX = [int](([int]$brushesTab.Groups[1].Value + [int]$brushesTab.Groups[3].Value) / 2)
    $tabY = [int](([int]$brushesTab.Groups[2].Value + [int]$brushesTab.Groups[4].Value) / 2)
    Invoke-Adb @('shell', 'input', 'tap', $tabX, $tabY) | Out-Null
    Start-Sleep -Milliseconds 500
    $window = Get-WindowXml
    for ($openAttempt = 0; $openAttempt -lt 2 -and $window -notmatch 'Biblioteca de pinceles'; $openAttempt++) {
        $brushesTab = Get-TextBounds $window 'Pinceles'
        if (-not $brushesTab.Success) { break }
        $tabX = [int](([int]$brushesTab.Groups[1].Value + [int]$brushesTab.Groups[3].Value) / 2)
        $tabY = [int](([int]$brushesTab.Groups[2].Value + [int]$brushesTab.Groups[4].Value) / 2)
        Invoke-Adb @('shell', 'input', 'tap', $tabX, $tabY) | Out-Null
        Start-Sleep -Milliseconds 700
        $window = Get-WindowXml
    }
    if ($window -notmatch 'Biblioteca de pinceles') { throw 'No se pudo abrir la biblioteca de pinceles.' }
    # Filtering is deterministic even when the dock has remembered a previous
    # scroll position, unlike repeatedly swiping a virtualized preset list.
    $search = Get-TextBounds $window 'Buscar pinceles'
    if ($search.Success) {
        $searchX = [int](([int]$search.Groups[1].Value + [int]$search.Groups[3].Value) / 2)
        $searchY = [int](([int]$search.Groups[2].Value + [int]$search.Groups[4].Value) / 2)
        Invoke-Adb @('shell', 'input', 'tap', $searchX, $searchY) | Out-Null
        Invoke-Adb @('shell', 'input', 'text', $Name) | Out-Null
        Start-Sleep -Milliseconds 450
    }
    $match = Get-TextBounds (Get-WindowXml) $Name
    for ($attempt = 0; $attempt -lt 5 -and -not $match.Success; $attempt++) {
        $window = Get-WindowXml
        $match = Get-TextBounds $window $Name
        if (-not $match.Success) {
            Invoke-Adb @('shell', 'input', 'swipe', '2200', '1250', '2200', '450', '300') | Out-Null
            Start-Sleep -Milliseconds 350
        }
    }
    if (-not $match.Success) { throw "No se encontró el preset '$Name' en el panel de pinceles." }
    $x = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
    $y = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
    Invoke-Adb @('shell', 'input', 'tap', $x, $y) | Out-Null
    Start-Sleep -Milliseconds 350
}

function Increase-BrushSize {
    for ($index = 0; $index -lt $BrushSizeIncrements; $index++) {
        # KEYCODE_RIGHT_BRACKET: DrawingView increases brush size by 16% per press.
        Invoke-Adb @('shell', 'input', 'keyevent', '72') | Out-Null
    }
}

if (-not $Serial) { $Serial = Get-ConnectedTablet }

if (-not $SkipBuild) {
    $javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
    if (-not (Test-Path $javaHome)) { throw "No se encontró JAVA_HOME: $javaHome" }
    $env:JAVA_HOME = $javaHome
    if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:KOTLIN_COMPILER_EXECUTION_STRATEGY = 'in-process'
    $env:Path = "$javaHome\bin;$env:Path"
    Push-Location $projectRoot
    try {
        & .\gradlew.bat assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación debug.' }
    } finally { Pop-Location }
}

if (-not (Test-Path $apkPath)) { throw "No se encontró el APK: $apkPath" }
if (-not $SkipInstall) { Invoke-Adb @('install', '--streaming', '-r', $apkPath) | Out-Host }

New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
Invoke-Adb @('logcat', '-c') | Out-Null
Invoke-Adb @('shell', 'am', 'force-stop', $packageName) | Out-Null
Invoke-Adb @('shell', 'am', 'start', '-n', "$packageName/com.orbyte.canvasstudio.MainActivity") | Out-Host
Start-Sleep -Seconds 5
if (-not (Open-TestProject)) {
    throw 'No se pudo abrir el editor. Ajusta -ProjectTapX/-ProjectTapY para la tarjeta de prueba.'
}
Invoke-Adb @('shell', 'dumpsys', 'gfxinfo', $packageName, 'reset') | Out-Null
$baselineScreenshot = Save-Screenshot 'baseline'

# 20 × 10 unique screen-space cells over the Tab S8 canvas. Unlike a repeated scribble,
# each mark has its own location so a missing tile/stroke is immediately visible in a capture.
$strokes = foreach ($row in 0..9) {
  foreach ($column in 0..19) {
    # Tab S8 editor input begins at x=340 in this tablet layout. Keep every unique gesture
    # inside the document rather than merely inside the visible workspace.
    $x = 340 + $column * 49
    $y = 400 + $row * 55
        # The unary comma keeps this coordinate tuple as one stroke; without it PowerShell
        # flattens the values and ADB receives incomplete swipe commands.
        , @($x, $y, ($x + 30), ($y + 20))
    }
}
$profiles = if ($BrushPresets.Count -gt 0) { $BrushPresets } else { @($null) }
$strokeIndex = 0
foreach ($profile in $profiles) {
    if ($profile) { Select-BrushPreset $profile }
    Increase-BrushSize
    $remainingProfiles = $profiles.Count - [array]::IndexOf($profiles, $profile)
    $strokesForProfile = [math]::Ceiling(($StrokeCount - $strokeIndex) / $remainingProfiles)
    for ($index = 0; $index -lt $strokesForProfile -and $strokeIndex -lt $StrokeCount; $index++) {
        $stroke = $strokes[$strokeIndex % $strokes.Count]
        if ($stroke.Count -ne 4) { throw "Coordenadas de trazo invÃ¡lidas en la celda $($strokeIndex + 1)." }
        Invoke-Adb @('shell', 'input', 'swipe', $stroke[0], $stroke[1], $stroke[2], $stroke[3], $StrokeDurationMs) | Out-Null
        $strokeIndex++
    }
}

Start-Sleep -Seconds 5 # Allow the incremental autosave to flush dirty tiles.
$beforeRestartScreenshot = Save-Screenshot 'before-restart'
$beforeRestartVisibility = Test-UniqueStrokeVisibility $baselineScreenshot $beforeRestartScreenshot $strokes $StrokeCount
$gfxInfo = Invoke-Adb @('shell', 'dumpsys', 'gfxinfo', $packageName)
$memory = Invoke-Adb @('shell', 'dumpsys', 'meminfo', $packageName)
Invoke-Adb @('shell', 'am', 'force-stop', $packageName) | Out-Null
Start-Sleep -Seconds 2
Invoke-Adb @('shell', 'am', 'start', '-n', "$packageName/com.orbyte.canvasstudio.MainActivity") | Out-Null
Start-Sleep -Seconds 5
if (-not (Open-TestProject)) {
    throw 'No se pudo reabrir el editor tras el reinicio. Revisa el proyecto de prueba y el informe.'
}
$afterRestartScreenshot = Save-Screenshot 'after-restart'
$afterRestartVisibility = Test-UniqueStrokeVisibility $baselineScreenshot $afterRestartScreenshot $strokes $StrokeCount
$focus = Invoke-Adb @('shell', 'dumpsys', 'window')
$logcat = Invoke-Adb @('logcat', '-d', '-v', 'brief', '*:S', 'AndroidRuntime:E', 'CanvasStudio:E')

$fatalPattern = 'FATAL EXCEPTION|OutOfMemoryError|Fatal signal|Illegal Capacity: -2147483648'
$failures = @($logcat -split "`r?`n" | Where-Object { $_ -match $fatalPattern })
$frames = [regex]::Match($gfxInfo, 'Total frames rendered:\s+(\d+)').Groups[1].Value
$janky = [regex]::Match($gfxInfo, 'Janky frames:\s+(\d+) \(([^)]+)\)').Groups
$pss = [regex]::Match($memory, 'TOTAL PSS:\s+(\d+)').Groups[1].Value

$report = [ordered]@{
    timestamp = (Get-Date).ToString('o'); serial = $Serial; package = $packageName; strokes = $StrokeCount
    brushPresets = $BrushPresets; brushSizeIncrements = $BrushSizeIncrements
    strokePattern = 'unique-grid-20x10'
    frameCount = $frames; jankyFrames = $janky[1].Value; jankyPercent = $janky[2].Value; totalPssKb = $pss
    processRestored = $focus -match [regex]::Escape($packageName); fatalErrors = $failures
    baselineScreenshot = $baselineScreenshot; beforeRestartScreenshot = $beforeRestartScreenshot; afterRestartScreenshot = $afterRestartScreenshot
    visibleBeforeRestart = $beforeRestartVisibility.visible; missingBeforeRestart = $beforeRestartVisibility.missing
    visibleAfterRestart = $afterRestartVisibility.visible; missingAfterRestart = $afterRestartVisibility.missing
    visualVerification = 'Automated: compares every stroke cell against the baseline capture.'
}

$baseName = Join-Path $reportDirectory "tablet-stress-$timestamp"
$report | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 "$baseName.json"
$gfxInfo | Set-Content -Encoding utf8 "$baseName-gfxinfo.txt"
$memory | Set-Content -Encoding utf8 "$baseName-meminfo.txt"
$logcat | Set-Content -Encoding utf8 "$baseName-logcat.txt"

if (-not $report.processRestored) { throw "La app no quedó en primer plano tras el reinicio. Revisa $baseName.json" }
if ($failures.Count -gt 0) { throw "Se detectaron errores fatales. Revisa $baseName-logcat.txt" }
if ($beforeRestartVisibility.visible -ne $StrokeCount) { throw "Faltan trazos antes del reinicio: $($beforeRestartVisibility.missing -join ', '). Revisa $baseName.json" }
if ($afterRestartVisibility.visible -ne $StrokeCount) { throw "Faltan trazos tras el reinicio: $($afterRestartVisibility.missing -join ', '). Revisa $baseName.json" }

Write-Host "Prueba completada: $baseName.json"
Write-Host "Trazos visibles: $($afterRestartVisibility.visible)/$StrokeCount | Frames: $frames | Janky: $($janky[1].Value) ($($janky[2].Value)) | PSS: $pss KB"
