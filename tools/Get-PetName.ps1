# tools/Get-PetName.ps1 - GitHub (dustinkirkland/petname) から公式単語辞書を wget2 で直接取得するコードネームロボット
$ErrorActionPreference = "Stop"

$binDir = Join-Path $PSScriptRoot "bin\petname"
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir -Force | Out-Null
}

$adjFile = Join-Path $binDir "adjectives.txt"
$namesFile = Join-Path $binDir "names.txt"

# GitHub の dustinkirkland/petname 公式リポジトリの raw URL
$adjUrl = "https://raw.githubusercontent.com/dustinkirkland/petname/master/usr/share/petname/small/adjectives.txt"
$namesUrl = "https://raw.githubusercontent.com/dustinkirkland/petname/master/usr/share/petname/small/names.txt"

# 1. wget2 --trust-server-names で GitHub から公式辞書を取得するにょろ
if ((-not (Test-Path $adjFile)) -or (-not (Test-Path $namesFile))) {
    Write-Host "🤖 wget2 ロボット（--trust-server-names）発動！GitHub (dustinkirkland/petname) から公式単語リストを取得中..." -ForegroundColor Yellow
    
    if (Get-Command wget2 -ErrorAction SilentlyContinue) {
        & wget2 --trust-server-names --no-check-certificate -O $adjFile $adjUrl
        & wget2 --trust-server-names --no-check-certificate -O $namesFile $namesUrl
    } else {
        Invoke-WebRequest -Uri $adjUrl -OutFile $adjFile
        Invoke-WebRequest -Uri $namesUrl -OutFile $namesFile
    }
}

# 2. ダウンロードした本物の GitHub 公式辞書から読み込みにょろ
if ((Test-Path $adjFile) -and (Test-Path $namesFile)) {
    $adjectives = Get-Content -Path $adjFile -Encoding UTF8
    $animals = Get-Content -Path $namesFile -Encoding UTF8
    
    $randomAdj = Get-Random -InputObject $adjectives
    $randomAnimal = Get-Random -InputObject $animals
    
    $codeName = "$randomAdj-$randomAnimal"
} else {
    $codeName = "warm-witch"
}

# 3. キャノニカルさんからのお手紙ログ
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "💌 Canonical (キャノニカル) さんからの親愛なるお手紙 (GitHub直取得版):" -ForegroundColor Yellow
Write-Host "「Hello Reader チームの智浩さん、GitHub から petname 公式辞書を受け取ってくれてありがとう！" -ForegroundColor White
Write-Host " 今回のコードネームは... 【 $codeName 】 に決定しました！」" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan

return $codeName
