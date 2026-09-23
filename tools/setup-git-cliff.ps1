# tools/setup-git-cliff.ps1 - git-cliff 導入＆自動ダウンロード呪文書
$ErrorActionPreference = "Stop"

Write-Host "=== git-cliff 導入＆初期化開始にょろ ===" -ForegroundColor Cyan

$binDir = Join-Path $PSScriptRoot "bin"
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir -Force | Out-Null
}

$cliffExe = Join-Path $binDir "git-cliff.exe"

# 1. git-cliff.exe が未取得の場合は wget2 --trust-server-names でダウンロードにょろ
if (-not (Test-Path $cliffExe)) {
    Write-Host "🤖 wget2 ロボット（--trust-server-names）発動！git-cliff を取得するにょろ..." -ForegroundColor Yellow
    
    $url = "https://github.com/orhun/git-cliff/releases/download/v2.6.1/git-cliff-2.6.1-x86_64-pc-windows-msvc.zip"
    $zipFile = Join-Path $binDir "git-cliff.zip"

    if (Get-Command wget2 -ErrorAction SilentlyContinue) {
        Write-Host "wget2 --trust-server-names を使って安全に取得中..." -ForegroundColor Cyan
        & wget2 --trust-server-names --no-check-certificate -O $zipFile $url
    } else {
        Write-Host "wget2 が見つからないため Invoke-WebRequest にフォールバックするにょろ..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $url -OutFile $zipFile
    }

    if (Test-Path $zipFile) {
        Write-Host "ダウンロードした ZIP を解凍するにょろ..." -ForegroundColor Cyan
        Expand-Archive -Path $zipFile -DestinationPath $binDir -Force
        
        $extractedExe = Get-ChildItem -Path $binDir -Filter "git-cliff.exe" -Recurse | Select-Object -First 1
        if ($extractedExe -and ($extractedExe.FullName -ne $cliffExe)) {
            Move-Item -Path $extractedExe.FullName -Destination $cliffExe -Force
        }
        
        Remove-Item -Path $zipFile -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path $cliffExe)) {
    Write-Error "git-cliff.exe の配置に失敗したにょろ..."
    exit 1
}

Write-Host "🎉 git-cliff.exe の準備完了にょろ: $cliffExe" -ForegroundColor Green

# 2. 初回の CHANGELOG.md を生成するにょろ (--config フラグを正確に指定)
Write-Host "CHANGELOG.md を自動生成するにょろ..." -ForegroundColor Cyan
$repoRoot = Join-Path $PSScriptRoot ".."
$changelogPath = Join-Path $repoRoot "CHANGELOG.md"

Set-Location $repoRoot
& $cliffExe --config "cliff.toml" --output $changelogPath

if (Test-Path $changelogPath) {
    Write-Host "✨ CHANGELOG.md の初回生成が成功したにょろ！" -ForegroundColor Green
}
