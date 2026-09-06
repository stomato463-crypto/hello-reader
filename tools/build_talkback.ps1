# TalkBack 専用自動ビルド＆デスクトップ同期スクリプトですにょろ。
Write-Host "--- TalkBack Build & Sync Script ---" -ForegroundColor Cyan

# 1. パスと環境変数の動的取得 (クロスプラットフォーム対応)
$userProfile = $env:USERPROFILE
if (-not $userProfile) { $userProfile = $env:HOME }

$desktopDir = Join-Path $userProfile "Desktop\talkback"
if (-not (Test-Path $desktopDir)) {
    New-Item -ItemType Directory -Path $desktopDir -Force | Out-Null
    Write-Host "デスクトップに talkback フォルダを作成したにょろ: $desktopDir" -ForegroundColor Green
}

# 2. talkback ソースコードフォルダの探索
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Split-Path -Parent $scriptDir
$talkbackDir = Join-Path $projectRoot "talkback"

if (-not (Test-Path $talkbackDir)) {
    if (Test-Path ".\build.gradle") {
        $talkbackDir = Get-Location
    } else {
        Write-Host "エラー: talkback ソースフォルダが見つからないにょろ！($talkbackDir)" -ForegroundColor Red
        Write-Host "git clone で talkback フォルダが用意されているか確認してね！" -ForegroundColor Yellow
        Exit
    }
}

Write-Host "TalkBack ソースフォルダ: $talkbackDir" -ForegroundColor Cyan

# 3. タイムスタンプと Git コマンドハッシュの取得
Push-Location $talkbackDir
$gitHash = "nohash"
try {
    $gitHash = (git rev-parse --short=8 HEAD 2>$null)
    if (-not $gitHash) { $gitHash = "nohash" }
} catch {
    $gitHash = "nohash"
}
Pop-Location

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$apkName = "talkback-debug-${gitHash}-${timestamp}.apk"

Write-Host "ビルド識別名: $apkName" -ForegroundColor Yellow

# 4. Gradle ビルドの実行 (TalkBack スマホ専用 assemblePhoneDebug ビルド)
Push-Location $talkbackDir

Write-Host "`n--- Gradle ビルドを開始するにょろ ---" -ForegroundColor Cyan

# talkback 内に gradlew.bat が無ければ hello-reader からラッパー一式を自動コピー！
if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "hello-reader から Gradle ラッパー一式を talkback フォルダへ自動移植中にょろ..." -ForegroundColor Yellow
    Copy-Item -Path (Join-Path $projectRoot "gradlew.bat") -Destination $talkbackDir -Force
    
    $wrapperTargetDir = Join-Path $talkbackDir "gradle\wrapper"
    if (-not (Test-Path $wrapperTargetDir)) {
        New-Item -ItemType Directory -Path $wrapperTargetDir -Force | Out-Null
    }
    Copy-Item -Path (Join-Path $projectRoot "gradle\wrapper\*") -Destination $wrapperTargetDir -Force
}

$buildLogFile = Join-Path $projectRoot "talkback_build_error.log"
# Kotlin JVM Target を 17 に固定する完全解決お守りパラメータ！
$buildArgs = "assemblePhoneDebug -Pkotlin.compiler.jvmTarget=17 -Pandroid.kotlinOptions.jvmTarget=17 -Pandroid.javaCompile.suppressJvmTargetValidation=true"

Write-Host "実行コマンド: cmd /c gradlew.bat $buildArgs" -ForegroundColor Yellow

cmd /c "gradlew.bat $buildArgs" 2>&1 | Tee-Object -FilePath $buildLogFile

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nビルドエラーが発生したにょろ！エラーログを保存したにょろ: talkback_build_error.log" -ForegroundColor Red
    Pop-Location
    Exit
}

Pop-Location

# 5. 生成された APK の探索とデスクトップ同期
$foundApks = Get-ChildItem -Path $talkbackDir -Recurse -Filter "*.apk" | Where-Object { $_.FullName -like "*debug*" }

if ($foundApks.Count -gt 0) {
    $sourceApk = $foundApks[0].FullName
    $destinationPath = Join-Path $desktopDir $apkName

    Copy-Item -Path $sourceApk -Destination $destinationPath -Force -Verbose
    
    Write-Host "`n========================================================" -ForegroundColor Green
    Write-Host "🎉 TalkBack APK のビルド＆同期が大成功したにょろ！" -ForegroundColor Green
    Write-Host "出力APK: $destinationPath" -ForegroundColor Cyan
    Write-Host "========================================================" -ForegroundColor Green
} else {
    Write-Host "エラー: 生成された APK ファイルが見つからなかったにょろ。" -ForegroundColor Red
}
