param(
    [Parameter(Mandatory = $true)][int]$TargetProcessId,
    [Parameter(Mandatory = $true)][long]$ExpectedStartTicks,
    [Parameter(Mandatory = $true)][string]$ExecutablePath
)
$ErrorActionPreference = 'Stop'
$logPath = Join-Path $PSScriptRoot '..\.tools\codex-host-restart.log'
function Write-Result([string]$Message) {
    Add-Content -LiteralPath $logPath -Value ((Get-Date -Format o) + ' ' + $Message)
}
try {
    $target = Get-Process -Id $TargetProcessId -ErrorAction Stop
    if ($target.Path -ne $ExecutablePath -or $target.StartTime.Ticks -ne $ExpectedStartTicks -or $target.ProcessName -ne 'ChatGPT') {
        throw 'Target identity changed; refusing to stop another process.'
    }
    if (-not (Test-Path -LiteralPath $ExecutablePath -PathType Leaf)) { throw 'Relaunch executable missing.' }
    Write-Result 'IDENTITY_VERIFIED'
    # Give the calling session time to receive the worker PID.
    Start-Sleep -Seconds 5
    $target.Refresh()
    if (-not $target.HasExited) {
        if ($target.MainWindowHandle -ne 0) {
            throw 'App now has a visible main window; refusing background-host termination.'
        } else {
            # User explicitly authorized restarting the background host.
            Write-Result 'STOPPING_WINDOWLESS_HOST'
            $target.Kill()
            if (-not $target.WaitForExit(10000)) { throw 'Background host did not exit within 10 seconds.' }
        }
    }
    Write-Result 'OLD_HOST_EXITED'
    Start-Process -FilePath $ExecutablePath -WindowStyle Hidden
    Write-Result 'RELAUNCH_REQUESTED'
    Start-Sleep -Seconds 3
    $new = @(Get-Process -Name ChatGPT -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $ExecutablePath -and $_.StartTime.Ticks -gt $ExpectedStartTicks })
    if ($new.Count -eq 0) { throw 'No new app process observed after relaunch.' }
    Write-Result ('NEW_PROCESS_OBSERVED IDs=' + (($new | Select-Object -ExpandProperty Id) -join ','))
} catch {
    Write-Result ('FAILED: ' + $_.Exception.Message)
    exit 1
}
