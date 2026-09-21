# tools/deploy.ps1 - ADB自動認識 ＆ インストール ＆ アクセシビリティ有効化スクリプト
$ErrorActionPreference = "Stop"

Write-Host "=== デバイス検出＆インストール開始にょろ ===" -ForegroundColor Cyan

# 1. ADBで接続中のデバイス一覧を取得（USB/WiFi問わず 'device' 状態の端末を自動検出）
$adbDevicesOutput = adb devices | Select-String -Pattern "\tdevice$"
if (-not $adbDevicesOutput) {
    Write-Error "接続中のオンライン端末（USB / WiFi）が見つからないにょろ！端末の接続とUSBデバッグを確認してにょろ！"
    exit 1
}

# 最初に見つかったデバイスIDを取得
$deviceId = ($adbDevicesOutput[0].Line -split "\t")[0]
Write-Host "ターゲット端末を自動検知したにょろ: $deviceId" -ForegroundColor Green

# 2. APKのパス確認
$apkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "インストール対象のAPK ($apkPath) が見つからないにょろ！先にビルドを実行してね！"
    exit 1
}

# 3. APKをインストール
Write-Host "PixelにAPKをインストール中にょろ..." -ForegroundColor Yellow
adb -s $deviceId install -r $apkPath

if ($LASTEXITCODE -ne 0) {
    Write-Error "APKのインストールに失敗したにょろ！"
    exit $LASTEXITCODE
}

# 3.5 古いサービスプロセスを停止（メモリ上のキャッシュをクリア）
Write-Host "HelloReader サービスを再起動中にょろ..." -ForegroundColor Yellow
adb -s $deviceId shell am force-stop com.stomato.helloreader

# 4. HelloReaderService アクセシビリティサービスを自動有効化
Write-Host "HelloReader アクセシビリティサービスを自動有効化中にょろ..." -ForegroundColor Yellow
adb -s $deviceId shell settings put secure enabled_accessibility_services com.stomato.helloreader/.HelloReaderService
adb -s $deviceId shell settings put secure accessibility_enabled 1

Write-Host "インストール＆アクセシビリティ自動有効化が完了したにょろ！" -ForegroundColor Green
