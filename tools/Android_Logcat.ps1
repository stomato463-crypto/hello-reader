# Android Logcat を実行し、デバイス選択・モード選択・自動保存を行うツールですにょろ。
Write-Host "--- Android Logcat Tool ---" -ForegroundColor Cyan

# 1. 接続されているデバイス一覧を取得
$devices = @(adb devices | Select-String -Pattern "device$" | ForEach-Object {
        $parts = $_.Line -split "\t"
        $devType = "USB"
        if ($parts[0] -like "*_tcp" -or $parts[0] -like "*:*") {
            $devType = "WiFi"
        }
        New-Object PSObject -Property @{ Id = $parts[0]; Type = $devType }
    })

if ($devices.Count -eq 0) {
    Write-Host "接続中の端末が見つからないにょろ！USBまたはWi-Fi接続を確認してね！" -ForegroundColor Red
    Exit
}

$selectedDevice = $null
if ($devices.Count -eq 1) {
    $selectedDevice = $devices[0].Id
    Write-Host "接続されているデバイスを自動選択したにょろ: $selectedDevice ($($devices[0].Type))" -ForegroundColor Green
}
else {
    Write-Host "複数デバイスを検知したにょろ。選択してね:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host "[$($i + 1)] ID: $($devices[$i].Id) ($($devices[$i].Type))"
    }
    $choice = ""
    while ($choice -lt 1 -or $choice -gt $devices.Count) {
        $choice = Read-Host "デバイスを選択 (1-$($devices.Count))"
        if ($choice -match '^\d+$') { $choice = [int]$choice } else { $choice = 0 }
    }
    $selectedDevice = $devices[$choice - 1].Id
}

# 2. ログ取得モードの選択
Write-Host "`n--- ログモードを選択してね ---" -ForegroundColor Cyan
Write-Host "[1] Default Mode (HelloReader & アクセシビリティ専用スリム表示)"
Write-Host "[2] Error Mode (エラーログのみ)"
Write-Host "[3] Full Mode (全ログ取得)"

$modeChoice = ""
while ($modeChoice -ne "1" -and $modeChoice -ne "2" -and $modeChoice -ne "3") {
    $modeChoice = Read-Host "モードを選択 (1-3)"
}

$modeName = ""
$logFilterArgs = ""

if ($modeChoice -eq "1") {
    $modeName = "DefaultMode"
    # HelloReaderServiceとアクセシビリティ関連のみをピンポイント取得（他は消音して超スリム化!）
    $logFilterArgs = "HelloReaderService:V HelloReader:V AccessibilityManager:V AccessibilityService:V *:S"
}
elseif ($modeChoice -eq "2") {
    $modeName = "ErrorMode"
    $logFilterArgs = "*:E"
}
else {
    $modeName = "FullMode"
    $logFilterArgs = ""
}

# 3. カレントパス（作業中ディレクトリ）の動的取得 (クロスプラットフォーム対応)
$currentDir = (Get-Location).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "${modeName}_${timestamp}.log"
$saveFullPath = Join-Path $currentDir $fileName

# スレッドセーフな同期リストを使用
$logList = [System.Collections.ArrayList]::Synchronized([System.Collections.ArrayList]::new())

$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = "adb"
$processInfo.Arguments = "-s $selectedDevice logcat -v time $logFilterArgs"
$processInfo.RedirectStandardOutput = $true
$processInfo.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $processInfo

Write-Host "`n--- [$modeName] でスリムログ収集を開始するにょろ ---" -ForegroundColor Cyan
Write-Host "[Ctrl+C] を押すと『トン・ツー♪』と鳴って即座に保存されるにょろ！" -ForegroundColor Yellow
Write-Host "保存予定場所 (カレントパス): $saveFullPath" -ForegroundColor Yellow
Write-Host "--------------------------------------------------------"

# 非同期イベント駆動でログを受信
$action = {
    if ($Event.SourceEventArgs.Data) {
        $line = $Event.SourceEventArgs.Data
        Write-Host $line
        [void]$Event.MessageData.Add($line)
    }
}

$eventSubscriber = Register-ObjectEvent -InputObject $process -EventName OutputDataReceived -Action $action -MessageData $logList

try {
    $process.Start() | Out-Null
    $process.BeginOutputReadLine()
    
    while (-not $process.HasExited) {
        Start-Sleep -Milliseconds 200
    }
}
finally {
    # 1. adb プロセスを即座に強制終了（暴走防止）
    if ($process -and -not $process.HasExited) {
        try {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            $process.Kill()
        } catch { }
    }
    
    # 🔔 Ctrl+Cキーが受け付けられたことを伝えるモールスディープ音（トン・ツー♪）
    try {
        [Console]::Beep(400, 100) # トン (低音400Hz)
        [Console]::Beep(600, 180) # ツゥー (中音600Hz)
    } catch { }

    Unregister-Event -SubscriptionId $eventSubscriber.Id -ErrorAction SilentlyContinue
    
    $linesToSave = @($logList)

    # ログファイルの保存（カレントパスへ確実に保存！）
    if ($linesToSave.Count -gt 0) {
        # 1000行を超えていたら直近の1000行だけに自動スリムカット！
        if ($linesToSave.Count -gt 1000) {
            $linesToSave = $linesToSave[($linesToSave.Count - 1000)..($linesToSave.Count - 1)]
            Write-Host "`n※ サーバー保護のため、直近最新1000行に自動スリムカットしたにょろ！" -ForegroundColor Yellow
        }

        $totalLines = $linesToSave.Count
        Write-Host "`nディスクにログを保存中にょろ... 合計行数: $totalLines" -ForegroundColor Yellow
        Write-Host "保存先フルパス: $saveFullPath" -ForegroundColor Cyan
            
        $stream = [System.IO.File]::Create($saveFullPath)
        $writer = New-Object System.IO.StreamWriter($stream, [System.Text.Encoding]::UTF8, 65536)
        
        try {
            $batchSize = [Math]::Max([int]($totalLines / 10), 1)
            for ($i = 0; $i -lt $totalLines; $i++) {
                $writer.WriteLine($linesToSave[$i])
                
                if ($i -gt 0 -and ((($i % $batchSize) -eq 0) -or ($i -eq ($totalLines - 1)))) {
                    $percent = [int]((($i + 1) / $totalLines) * 100)
                    Write-Host "保存進捗: $percent% ($($i + 1) / $totalLines 行)" -ForegroundColor Cyan
                }
            }
        }
        finally {
            $writer.Flush()
            $writer.Close()
            $stream.Close()
        }

        # 🎵 保存完了を知らせるハッピーな完了ディープ音♪
        try {
            [Console]::Beep(800, 150)
        } catch { }

        Write-Host "ログキャッチ終了だにょろ！" -ForegroundColor Yellow
        Write-Host "ログ保存完了にょろ: [ $saveFullPath ] (合計行数: $totalLines)" -ForegroundColor Green
    }
    else {
        Write-Host "`nログキャッチ終了。取得されたログはゼロ件だったにょろ。" -ForegroundColor Red
        Write-Host "保存予定だったパス: $saveFullPath" -ForegroundColor Yellow
    }
}