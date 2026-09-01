# run_all.ps1 - Automated build, deploy, launch, and log collection script
param (
    [alias("c")][switch]$Clean,
    [alias("l")][string]$Log = $null,
    [alias("w")][switch]$Wait
)

$ErrorActionPreference = "Stop"

Write-Host "🚀 Starting Hello Reader fully automated workflow..." -ForegroundColor Magenta

$toolsDir = Join-Path $PSScriptRoot "tools"

# 1. Clean step: Run gradlew clean if -c is specified
if ($Clean) {
    Write-Host "`n[Clean Step] Running Gradle clean (-c)..." -ForegroundColor Yellow
    & "$PSScriptRoot\gradlew.bat" clean
}

# 2. Build step
Write-Host "`n[Step 1/3] Building APK..." -ForegroundColor Cyan
& (Join-Path $toolsDir "build.ps1")

# 3. Deploy and enable accessibility service step
Write-Host "`n[Step 2/3] Deploying and enabling Accessibility Service..." -ForegroundColor Cyan
& (Join-Path $toolsDir "deploy.ps1")

# 4. Launch app step
Write-Host "`n[Step 3/3] Launching application..." -ForegroundColor Cyan
& (Join-Path $toolsDir "launch.ps1")

# 5. Log step: Start Logcat collector if -l is specified
if ($PSBoundParameters.ContainsKey('Log')) {
    Write-Host "`n[Log Step] Starting Hello Reader Logcat collector (-l)..." -ForegroundColor Magenta
    $logcatScript = Join-Path $toolsDir "Android_Logcat.ps1"
    if (Test-Path $logcatScript) {
        & $logcatScript
    } else {
        adb logcat -v time HelloReaderService:D *:S
    }
}

Write-Host "`n🎉 All automated workflow processes completed successfully!" -ForegroundColor Green

# 6. Wait step: Pause if -w is specified
if ($Wait) {
    Read-Host "`n[Wait Step] Press Enter to exit (-w)..."
}
