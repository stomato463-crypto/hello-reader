# TalkBack 専用 Android Logcat を実行し、デバイス選択・モード選択・自動保存を行うツールですにょろ。
Write-Host "--- TalkBack Logcat Tool ---" -ForegroundColor Cyan

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
Write-Host "`n--- TalkBack ログモードを選択してね ---" -ForegroundColor Cyan
Write-Host "[1] Default Mode (TalkBack本体のみピンポイント超スリム表示)"
Write-Host "[2] Error Mode (エラーログのみ)"
Write-Host "[3] Full Mode (全ログ取得)"

$modeChoice = ""
while ($modeChoice -ne "1" -and $modeChoice -ne "2" -and $modeChoice -ne "3") {
    $modeChoice = Read-Host "モードを選択 (1-3)"
}

$modeName = ""
$logFilterArgs = ""

if ($modeChoice -eq "1") {
    $modeName = "TalkBackDefaultMode"
    # HelloReader同様、TalkBack本体タグのみをピンポイント取得し、OSの雑音を消音して30行級スリム化!
    $logFilterArgs = "TalkBack:V TalkBackService:V LogUtils:V GestureController:V SpeechController:V EventDumper:V *:S"
}
elseif ($modeChoice -eq "2") {
    $modeName = "TalkBackErrorMode"
    $logFilterArgs = "*:E"
}
else {
    $modeName = "TalkBackFullMode"
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

Write-Host "`n--- [$modeName] でTalkBack超スリムログ収集を開始するにょろ ---" -ForegroundColor Cyan
Write-Host "[Ctrl+C] を押すと『トン・ツー♪』と鳴って即座に全行保存されるにょろ！" -ForegroundColor Yellow
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
        if ($linesToSave.Count -gt 1000) {
            $linesToSave = $linesToSave[($linesToSave.Count - 1000)..($linesToSave.Count - 1)]
            Write-Host "`n※ サーバー保護のため、直近最新1000行に自動スリムカットしたにょろ！" -ForegroundColor Yellow
        }

        $totalLines = $linesToSave.Count
        Write-Host "`nディスクにTalkBackログを保存中にょろ... 合計行数: $totalLines 行" -ForegroundColor Yellow
        Write-Host "保存先フルパス: $saveFullPath" -ForegroundColor Cyan
            
        $stream = [System.IO.File]::Create($saveFullPath)
        $writer = New-Object System.IO.StreamWriter($stream, [System.Text.Encoding]::UTF8, 65536)
        
        function Sanitize-LogLine([string]$l) {
            if (-not $l) { return "" }
            $l = $l -replace '(\d{2,4}-\d{2,4}-\d{4}|\d{10,11})', '[電話番号マスク済]'
            $l = $l -replace '(Speaking:\s*(通知:\s*)?)(.+?)(さん|氏|様)', '$1[個人名マスク済]$4'
            return $l
        }

        try {
            $batchSize = [Math]::Max([int]($totalLines / 10), 1)
            for ($i = 0; $i -lt $totalLines; $i++) {
                $sanitizedLine = Sanitize-LogLine $linesToSave[$i]
                $writer.WriteLine($sanitizedLine)
                
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

        Write-Host "TalkBackログキャッチ終了だにょろ！" -ForegroundColor Yellow
        Write-Host "全ログ保存完了にょろ: [ $saveFullPath ] (合計行数: $totalLines 行)" -ForegroundColor Green
    }
    else {
        Write-Host "`nログキャッチ終了。取得されたログはゼロ件だったにょろ。" -ForegroundColor Red
        Write-Host "保存予定だったパス: $saveFullPath" -ForegroundColor Yellow
    }
}
