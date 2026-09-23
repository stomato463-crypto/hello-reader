# tools/setup-git-cliff.ps1 - Hello Reader 専用 Wチェンジログ (Markdown & Hello-Reader規格) 自動生成呪文書
$ErrorActionPreference = "Stop"

Write-Host "=== Hello Reader Wチェンジログ (Markdown & Hello-Reader規格) 生成開始にょろ ===" -ForegroundColor Cyan

# 0. キャノニカルさんからのコードネームお手紙ロボットを呼び出しにょろ
$petnameScript = Join-Path $PSScriptRoot "Get-PetName.ps1"
if (Test-Path $petnameScript) {
    $codeName = & $petnameScript
}

$binDir = Join-Path $PSScriptRoot "bin"
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir -Force | Out-Null
}

$cliffExe = Join-Path $binDir "git-cliff.exe"

# 1. git-cliff.exe が未取得の場合は wget2 --trust-server-names で取得にょろ
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

$repoRoot = Join-Path $PSScriptRoot ".."
Set-Location $repoRoot

# 2. Markdown 版 CHANGELOG.md の生成にょろ
Write-Host "1. Hello Reader Markdown版 CHANGELOG.md を生成中..." -ForegroundColor Cyan
$markdownPath = Join-Path $repoRoot "CHANGELOG.md"
& $cliffExe --config "cliff.toml" --output $markdownPath

if (Test-Path $markdownPath) {
    Write-Host "  ✨ CHANGELOG.md 生成完了にょろ！" -ForegroundColor Green
}

# 3. Hello-Reader 規格版 hello-reader/changelog の生成にょろ
Write-Host "2. Hello-Reader 規格版 hello-reader/changelog を生成中..." -ForegroundColor Cyan
$helloReaderDir = Join-Path $repoRoot "hello-reader"
if (-not (Test-Path $helloReaderDir)) {
    New-Item -ItemType Directory -Path $helloReaderDir -Force | Out-Null
}

$helloReaderChangelogPath = Join-Path $helloReaderDir "changelog"
& $cliffExe --config "cliff-debian.toml" --output $helloReaderChangelogPath

if (Test-Path $helloReaderChangelogPath) {
    Write-Host "  ✨ hello-reader/changelog 生成完了にょろ！" -ForegroundColor Green
}

Write-Host "🎉 Hello-Reader 専用 Wチェンジログ＆コードネームの構築完了だにょろ！" -ForegroundColor Green
