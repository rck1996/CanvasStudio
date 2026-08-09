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
$runDirectory = Join-Path $root "build-logs\instrumentation-$stamp"
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
    $combined = $standardOutput + "`n" + $standardError
    $passed = -not $timedOut -and $combined -match 'OK \(\d+ tests?\)' -and
        $combined -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg='
    $status = if ($timedOut) { "TIMEOUT" } elseif ($passed) { "PASS" } else { "FAIL" }
    $summary = ([regex]::Match($combined, 'OK \(\d+ tests?\)|FAILURES!!!|INSTRUMENTATION_FAILED[^\r\n]*').Value)
    Write-State $className $status $started $summary
    $results += [pscustomobject]@{
        className = $className
        status = $status
        durationSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
        processExitCode = if ($process.HasExited) { $process.ExitCode } else { $null }
        log = $stdout
    }
}

$results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDirectory "results.json") -Encoding utf8
$failed = @($results | Where-Object status -ne "PASS")
$finalStatus = if ($failed.Count -eq 0) { "PASS" } else { "FAIL" }
Write-State "ALL" $finalStatus (Get-Date) "$($results.Count - $failed.Count)/$($results.Count) clases"
Write-Output "RESULTS=$runDirectory\results.json"
if ($failed.Count -gt 0) { exit 1 }
