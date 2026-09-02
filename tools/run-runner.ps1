$runnerPath = $env:GITEA_RUNNER_PATH

if (-not $runnerPath -or -not (Test-Path $runnerPath)) {
    $cmd = Get-Command "gitea-runner.exe" -ErrorAction SilentlyContinue
    if ($cmd) {
        $runnerPath = $cmd.Source
    } else {
        $runnerPath = Join-Path $env:SystemDrive "gitea\gitea-runner.exe"
    }
}

if (Test-Path $runnerPath) {
    & $runnerPath daemon
} else {
    Write-Error "gitea-runner.exe not found. Please set GITEA_RUNNER_PATH environment variable."
}