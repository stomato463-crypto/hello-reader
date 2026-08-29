# run_all.ps1 - 全自動ビルド・デプロイ・起動統合スクリプト
$ErrorActionPreference = "Stop"

Write-Host "🚀 Hello Reader 全自動ワークフローをスタートするにょろ！" -ForegroundColor Magenta

$toolsDir = Join-Path $PSScriptRoot "tools"

# 1. ビルド実行
Write-Host "`n[Step 1/3] ビルド実行中..." -ForegroundColor Cyan
& (Join-Path $toolsDir "build.ps1")

# 2. デプロイ＆有効化実行
Write-Host "`n[Step 2/3] デプロイ＆サービス有効化中..." -ForegroundColor Cyan
& (Join-Path $toolsDir "deploy.ps1")

# 3. 起動実行
Write-Host "`n[Step 3/3] アプリ起動中..." -ForegroundColor Cyan
& (Join-Path $toolsDir "launch.ps1")

Write-Host "`n🎉 すべてのプロセスが全自動で大成功したにょろ！" -ForegroundColor Green
