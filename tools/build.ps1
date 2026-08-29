# tools/build.ps1 - Hello Reader デバッグビルドスクリプト
$ErrorActionPreference = "Stop"

Write-Host "=== Hello Reader デバッグビルド開始にょろ ===" -ForegroundColor Cyan

# 1. Gradleビルド実行
$gradleCmd = Join-Path $PSScriptRoot "..\gradlew.bat"
if (-not (Test-Path $gradleCmd)) {
    $gradleCmd = "gradlew"
}

& $gradleCmd assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Error "ビルドに失敗したにょろ...！"
    exit $LASTEXITCODE
}

# 2. Gitコミットハッシュ(先頭8文字)と日時を取得
$gitHash = "unknown"
try {
    $gitHash = (git rev-parse --short=8 HEAD).Trim()
} catch {
    Write-Host "Gitハッシュの取得をスキップしたにょろ" -ForegroundColor Yellow
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$apkFileName = "debug-$gitHash-$timestamp.apk"

# 3. 出力先（デスクトップの hello-reader フォルダ）の準備
$desktopPath = [System.IO.Path]::Combine([System.Environment]::GetFolderPath("Desktop"), "hello-reader")
if (-not (Test-Path $desktopPath)) {
    New-Item -ItemType Directory -Path $desktopPath -Force | Out-Null
}

$builtApk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
$destApk = Join-Path $desktopPath $apkFileName

if (Test-Path $builtApk) {
    Copy-Item -Path $builtApk -Destination $destApk -Force
    # 互換用 app-hello-reader-debug.apk もコピー
    $helloReaderApk = Join-Path $desktopPath "app-hello-reader-debug.apk"
    Copy-Item -Path $builtApk -Destination $helloReaderApk -Force
    Write-Host "ビルド成功にょろ！APKを出力したにょろ: $destApk" -ForegroundColor Green
} else {
    Write-Error "ビルド成果物 (app-debug.apk) が見つからないにょろ..."
}
