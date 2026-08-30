# run_all.ps1 - 全自動ビルド・デプロイ・起動・ログ収集統合スクリプト
param (
    [alias("c")][switch]$Clean,
    [alias("l")][switch]$Log,
    [alias("w")][switch]$Wait
)

$ErrorActionPreference = "Stop"

Write-Host "🚀 Hello Reader 全自動ワークフローをスタートするにょろ！" -ForegroundColor Magenta

$toolsDir = Join-Path $PSScriptRoot "tools"

# 1. Clean オプション指定時は gradlew clean を実行
if ($Clean) {
    Write-Host "`n[Clean Step] Gradle クリーン実行中 (-c)..." -ForegroundColor Yellow
    & "$PSScriptRoot\gradlew.bat" clean
}

# 2. ビルド実行
Write-Host "`n[Step 1/3] ビルド実行中..." -ForegroundColor Cyan
& (Join-Path $toolsDir "build.ps1")

# 3. デプロイ＆有効化実行
Write-Host "`n[Step 2/3] デプロイ＆サービス有効化中..." -ForegroundColor Cyan
& (Join-Path $toolsDir "deploy.ps1")

# 4. 起動実行
Write-Host "`n[Step 3/3] アプリ起動中..." -ForegroundColor Cyan
& (Join-Path $toolsDir "launch.ps1")

# 5. Log オプション指定時は ログキャッチツールを起動
if ($Log) {
    Write-Host "`n[Log Step] リアルタイムログキャッチをスタートするにょろ (-l)..." -ForegroundColor Magenta
    $logcatScript = Join-Path $toolsDir "TalkBack_Logcat.ps1"
    if (Test-Path $logcatScript) {
        & $logcatScript
    } else {
        adb logcat -v time HelloReaderService:D *:S
    }
}

Write-Host "`n🎉 すべてのプロセスが全自動で大成功したにょろ！" -ForegroundColor Green

# 6. Wait オプション指定時は 一時停止
if ($Wait) {
    Read-Host "`n[Wait Step] 終了するには Enter キーを押してね (-w)..."
}
