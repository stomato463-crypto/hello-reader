# tools/launch.ps1 - アプリ起動スクリプト
$ErrorActionPreference = "Stop"

Write-Host "=== Hello Reader 起動開始にょろ ===" -ForegroundColor Cyan

# 1. 接続デバイス自動検出
$adbDevicesOutput = adb devices | Select-String -Pattern "\tdevice$"
if (-not $adbDevicesOutput) {
    Write-Error "接続中の端末が見つからないにょろ！"
    exit 1
}

$deviceId = ($adbDevicesOutput[0].Line -split "\t")[0]

# 2. MainActivityを起動（標準権限ダイアログ等をユーザーに提示）
Write-Host "Pixelで Hello Reader (MainActivity) を起動中にょろ..." -ForegroundColor Yellow
adb -s $deviceId shell am start -n com.stomato.helloreader/.MainActivity

Write-Host "アプリ起動成功だにょろ！" -ForegroundColor Green
