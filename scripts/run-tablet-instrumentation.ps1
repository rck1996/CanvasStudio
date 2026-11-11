param(
    [string]$Serial = "R52W404GGPK",
    [string[]]$Classes = @(),
    [int]$PerClassTimeoutMinutes = 8,
    [int]$HeartbeatSeconds = 15,
    [switch]$IncludeEndurance
)

$ErrorActionPreference = "Stop"
$adb = if ($env:ANDROID_HOME) {
    Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
}
if (-not (Test-Path -LiteralPath $adb)) { throw "ADB no encontrado: $adb" }

$root = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runId = [guid]::NewGuid().ToString("N")
$runDirectory = Join-Path $root "build-logs\instrumentation-$stamp-$runId"
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$eventsFile = Join-Path $runDirectory "events.log"
$stateFile = Join-Path $runDirectory "state.json"

if ($Classes.Count -eq 0) {
    $Classes = Get-ChildItem -LiteralPath (Join-Path $root "app\src\androidTest\java") -Recurse -Filter "*Test.kt" |
        ForEach-Object {
            $source = Get-Content -LiteralPath $_.FullName -Raw
            $package = [regex]::Match($source, '(?m)^package\s+([\w.]+)').Groups[1].Value
            $class = [regex]::Match($source, '(?m)^(?:internal\s+)?class\s+(\w+Test)\b').Groups[1].Value
            if ($package -and $class) { "$package.$class" }
        } |
        Sort-Object -Unique
}
if (-not $IncludeEndurance) {
    $Classes = @($Classes | Where-Object { $_ -notmatch 'EnduranceTest$' })
}

function Write-State([string]$ClassName, [string]$Status, [datetime]$Started, [string]$Detail = "") {
    $state = [ordered]@{
        updatedAt = (Get-Date).ToString("o")
        device = $Serial
        currentClass = $ClassName
        status = $Status
        elapsedSeconds = [math]::Round(((Get-Date) - $Started).TotalSeconds, 1)
        detail = $Detail
        runDirectory = $runDirectory
    }
    $state | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding utf8
    $line = "{0} | {1} | {2} | {3}s | {4}" -f $state.updatedAt, $Status, $ClassName, $state.elapsedSeconds, $Detail
    $line | Tee-Object -FilePath $eventsFile -Append
}

function Get-InstrumentationResult([string]$StandardOutput, [string]$StandardError, [int]$ExitCode, [bool]$TimedOut) {
    $combined = $StandardOutput + "`n" + $StandardError
    $failure = [regex]::Match($combined, 'FAILURES!!!|INSTRUMENTATION_FAILED[^\r\n]*|shortMsg=[^\r\n]*')
    $success = [regex]::Match($StandardOutput, '(?m)^OK \([1-9][0-9]* tests?\)[ \t]*\r?$')
    # AndroidJUnitRunner's -1 is a successful terminal result, separate from the host exit code.
    $completion = [regex]::Match($StandardOutput, '(?m)^INSTRUMENTATION_CODE:[ \t]*-1\s*\z')

    if ($TimedOut) { return @{ Status = "TIMEOUT"; Detail = "Tiempo limite por clase agotado" } }
    if ($ExitCode -ne 0) { return @{ Status = "FAIL"; Detail = "ADB exit=$ExitCode" } }
    if ($failure.Success) { return @{ Status = "FAIL"; Detail = $failure.Value } }
    if (-not $success.Success -or -not $completion.Success -or $success.Index -ge $completion.Index) {
        return @{ Status = "FAIL"; Detail = "Sin cierre exitoso de AndroidJUnitRunner con pruebas ejecutadas" }
    }
    return @{ Status = "PASS"; Detail = $success.Value.TrimEnd() }
}

$results = @()
foreach ($className in $Classes) {
    $safeName = $className -replace '[^A-Za-z0-9_.-]', '_'
    $stdout = Join-Path $runDirectory "$safeName.stdout.log"
    $stderr = Join-Path $runDirectory "$safeName.stderr.log"
    $started = Get-Date
    Write-State $className "START" $started
    $arguments = @(
        "-s", $Serial,
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", $className,
        "com.orbyte.canvasstudio.debug.test/androidx.test.runner.AndroidJUnitRunner"
    )
    if ($IncludeEndurance) {
        $arguments = @(
            "-s", $Serial,
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", $className,
            "-e", "notAnnotation", "none",
            "com.orbyte.canvasstudio.debug.test/androidx.test.runner.AndroidJUnitRunner"
        )
    }
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $adb
    $processInfo.Arguments = ($arguments -join ' ')
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    if (-not $process.Start()) { throw "No se pudo iniciar ADB para $className" }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $timedOut = $false
    while (-not $process.HasExited) {
        Start-Sleep -Seconds $HeartbeatSeconds
        $process.Refresh()
        if ($process.HasExited) { break }
        $elapsed = ((Get-Date) - $started).TotalMinutes
        Write-State $className "RUNNING" $started "pid=$($process.Id)"
        if ($elapsed -ge $PerClassTimeoutMinutes) {
            $timedOut = $true
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            & $adb -s $Serial shell am force-stop com.orbyte.canvasstudio.debug | Out-Null
            & $adb -s $Serial shell am force-stop com.orbyte.canvasstudio.debug.test | Out-Null
            break
        }
    }
    $process.WaitForExit()
    $standardOutput = $stdoutTask.Result
    $standardError = $stderrTask.Result
    $standardOutput | Set-Content -LiteralPath $stdout -Encoding utf8
    $standardError | Set-Content -LiteralPath $stderr -Encoding utf8
    $exitCode = $process.ExitCode
    $outcome = Get-InstrumentationResult $standardOutput $standardError $exitCode $timedOut
    Write-State $className $outcome.Status $started $outcome.Detail
    $results += [pscustomobject]@{
        className = $className
        status = $outcome.Status
        durationSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
        processExitCode = $exitCode
        log = $stdout
    }
}

$resultsFile = Join-Path $runDirectory "results.json"
ConvertTo-Json -InputObject $results | Set-Content -LiteralPath $resultsFile -Encoding utf8
$failed = @($results | Where-Object status -ne "PASS")
$allPassed = $results.Count -gt 0 -and $failed.Count -eq 0
$finalStatus = if ($allPassed) { "PASS" } else { "FAIL" }
$finalDetail = if ($results.Count -eq 0) { "No hay clases seleccionadas" } else {
    "$($results.Count - $failed.Count)/$($results.Count) clases"
}
Write-State "ALL" $finalStatus (Get-Date) $finalDetail
Write-Output "RESULTS=$resultsFile"
if (-not $allPassed) { exit 1 }
exit 0
