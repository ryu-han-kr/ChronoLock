param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$Offline,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot) { $SdkRoot = Join-Path $projectRoot '.tools/android-sdk' }
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "ADB not found: $adb" }
if ($JdkHome) { $env:JAVA_HOME = $JdkHome }
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools/android-user-home'
$reportDir = Join-Path $projectRoot ('.tools/device-verification-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null

function Invoke-Adb([string[]]$Arguments) {
    # Windows PowerShell 5.1 treats any native stderr (e.g. adb pull progress) as fatal under Stop; rely on the exit code.
    $ErrorActionPreference = 'Continue'
    $output = & $adb -s $Serial @Arguments 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')`n$($output -join "`n")" }
    return ($output -join "`n")
}

# These scenarios reboot and temporarily change permissions. Use a disposable emulator.
if ((Invoke-Adb @('shell', 'getprop', 'ro.kernel.qemu')).Trim() -ne '1') {
    throw 'Automated device scenarios require an emulator. Follow docs/DEVICE_TEST_PLAN.md on real hardware.'
}

if (-not $SkipBuild) {
    Push-Location (Join-Path $projectRoot 'android')
    try {
        $buildArgs = @('-PdeviceVerification=true', ':app:assembleDebug', ':app:assembleDebugAndroidTest', '--console=plain')
        if ($Offline) { $buildArgs += '--offline' }
        & .\gradlew.bat @buildArgs *> (Join-Path $reportDir 'build.log')
        if ($LASTEXITCODE -ne 0) { throw "Build failed; see $reportDir/build.log" }
    } finally { Pop-Location }
}

$apks = @(
    (Join-Path $projectRoot 'android/app/build/outputs/apk/debug/app-debug.apk'),
    (Join-Path $projectRoot 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
)
# Check both IDs before installing, so -SkipBuild cannot accidentally update the user's normal app.
$metadataFiles = @(
    (Join-Path (Split-Path $apks[0]) 'output-metadata.json'),
    (Join-Path (Split-Path $apks[1]) 'output-metadata.json')
)
$expectedIds = @('com.chronolock.verification', 'com.chronolock.verification.test')
for ($index = 0; $index -lt 2; $index++) {
    $metadata = Get-Content -Raw -LiteralPath $metadataFiles[$index] | ConvertFrom-Json
    if ($metadata.applicationId -ne $expectedIds[$index]) { throw 'Build the isolated APKs with -PdeviceVerification=true first.' }
}
foreach ($apk in $apks) {
    Copy-Item -LiteralPath $apk -Destination $reportDir
    Invoke-Adb @('install', '-r', $apk)
}

$originalServices = (Invoke-Adb @('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')).Trim()
$originalAirplane = (Invoke-Adb @('shell', 'settings', 'get', 'global', 'airplane_mode_on')).Trim()
$runner = 'com.chronolock.verification.test/androidx.test.runner.AndroidJUnitRunner'
$testClass = 'com.chronolock.blocking.DeviceBlockingTest'
function Invoke-Scenario([string]$Name, [string]$Selection, [string]$Stage = '') {
    Write-Output "Running $Name"
    $arguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'deviceVerification', 'true', '-e', 'class', $Selection)
    if ($Stage) { $arguments += @('-e', 'rebootStage', $Stage) }
    $result = Invoke-Adb ($arguments + $runner)
    $result | Set-Content -LiteralPath (Join-Path $reportDir "$Name.log") -Encoding UTF8
    if ($result -notmatch 'OK \([1-9][0-9]* tests?\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        throw "Scenario failed: $Name. See $reportDir"
    }
    Write-Output "Passed $Name"
}

try {
    $methods = @(
        'daily60Seconds_blocksOffline_andKeepsBlockingOnReturn',
        'fourthOpen_blocks_butSameAppActivityDoesNotCountAgain',
        'cooldown30Seconds_retriesDoNotExtendDeadline',
        'sessionLimit_survivesAccessibilityReconnect_andSettingsRemainReachable',
        'screenLock_doesNotAccumulateUsage',
        'revokingUsagePermission_isDetected_andRestoringItRecovers',
        'deletingRule_removesActiveOverlay'
    )
    Invoke-Scenario 'blocking' (($methods | ForEach-Object { "$testClass#$_" }) -join ',')
    Invoke-Scenario 'reboot-seed' "$testClass#rebootCheckpoint" 'seed'
    Write-Output 'Rebooting the test emulator'
    Invoke-Adb @('reboot')
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        # Windows PowerShell 5.1 turns adb's "device offline" stderr into a terminating error under Stop.
        $booted = try { & $adb -s $Serial shell getprop sys.boot_completed 2>$null } catch { '' }
        if ($LASTEXITCODE -eq 0 -and "$booted".Trim() -eq '1') { break }
    } while ((Get-Date) -lt $deadline)
    if ("$booted".Trim() -ne '1') { throw 'Emulator did not complete reboot within three minutes.' }
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    Invoke-Adb @('shell', 'wm', 'dismiss-keyguard')
    Invoke-Scenario 'reboot-verify' "$testClass#rebootCheckpoint" 'verify'
    Invoke-Adb @('pull', '/sdcard/Android/data/com.chronolock.verification/files/device-verification', $reportDir)
    Write-Output "All device scenarios passed. Reports: $reportDir"
} finally {
    # Restore device settings even when an assertion fails or a test process crashes.
    Invoke-Adb @('shell', 'am', 'force-stop', 'com.chronolock.verification')
    if ($originalServices -eq 'null' -or -not $originalServices) {
        Invoke-Adb @('shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services')
    } else {
        Invoke-Adb @('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', $originalServices)
    }
    $airplane = if ($originalAirplane -eq '1') { 'enable' } else { 'disable' }
    Invoke-Adb @('shell', 'cmd', 'connectivity', 'airplane-mode', $airplane)
    Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    Invoke-Adb @('shell', 'wm', 'dismiss-keyguard')
}
