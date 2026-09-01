# Android Logcat tool with device selection, mode selection, and auto-save capabilities.
Write-Host "--- Android Logcat Tool ---" -ForegroundColor Cyan

# 1. Get list of connected devices
$devices = @(adb devices | Select-String -Pattern "device$" | ForEach-Object {
        $parts = $_.Line -split "\t"
        $devType = "USB"
        if ($parts[0] -like "*_tcp" -or $parts[0] -like "*:*") {
            $devType = "WiFi"
        }
        New-Object PSObject -Property @{ Id = $parts[0]; Type = $devType }
    })

if ($devices.Count -eq 0) {
    Write-Host "No connected devices found! Please check USB or Wi-Fi connection." -ForegroundColor Red
    Exit
}

$selectedDevice = $null
if ($devices.Count -eq 1) {
    $selectedDevice = $devices[0].Id
    Write-Host "Auto-selected connected device: $selectedDevice ($($devices[0].Type))" -ForegroundColor Green
}
else {
    Write-Host "Multiple devices detected. Please select one:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host "[$($i + 1)] ID: $($devices[$i].Id) ($($devices[$i].Type))"
    }
    $choice = ""
    while ($choice -lt 1 -or $choice -gt $devices.Count) {
        $choice = Read-Host "Select device (1-$($devices.Count))"
        if ($choice -match '^\d+$') { $choice = [int]$choice } else { $choice = 0 }
    }
    $selectedDevice = $devices[$choice - 1].Id
}

# 2. Select log collection mode
Write-Host "`n--- Select Log Mode ---" -ForegroundColor Cyan
Write-Host "[1] Default Mode (Slim view for HelloReader & Accessibility)"
Write-Host "[2] Error Mode (HelloReader & Accessibility error logs only)"
Write-Host "[3] Full Mode (All HelloReader debug & trace logs)"

$modeChoice = ""
while ($modeChoice -ne "1" -and $modeChoice -ne "2" -and $modeChoice -ne "3") {
    $modeChoice = Read-Host "Select mode (1-3)"
}

$modeName = ""
$logFilterArgs = ""

if ($modeChoice -eq "1") {
    $modeName = "DefaultMode"
    # Pinpoint capture for HelloReaderService and Accessibility (filters out others for slim output)
    $logFilterArgs = "HelloReaderService:V HelloReader:V AccessibilityManager:V AccessibilityService:V *:S"
}
elseif ($modeChoice -eq "2") {
    $modeName = "ErrorMode"
    # HelloReader and Accessibility error logs only
    $logFilterArgs = "HelloReaderService:E HelloReader:E AccessibilityManager:E AccessibilityService:E *:S"
}
else {
    $modeName = "FullMode"
    # All levels of HelloReader logs
    $logFilterArgs = "HelloReaderService:V HelloReader:V *:S"
}

# 3. Dynamic current path resolution (Cross-platform support)
$currentDir = (Get-Location).Path
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "${modeName}_${timestamp}.log"
$saveFullPath = Join-Path $currentDir $fileName

# Thread-safe synchronized list for logs
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

Write-Host "`n--- Starting log collection in [$modeName] ---" -ForegroundColor Cyan
Write-Host "Press [Ctrl+C] to stop and save logs immediately!" -ForegroundColor Yellow
Write-Host "Target save path: $saveFullPath" -ForegroundColor Yellow
Write-Host "--------------------------------------------------------"

# Asynchronous event handler for log data
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
    # 1. Force stop adb process
    if ($process -and -not $process.HasExited) {
        try {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            $process.Kill()
        } catch { }
    }
    
    # Beep feedback on Ctrl+C stop
    try {
        [Console]::Beep(400, 100)
        [Console]::Beep(600, 180)
    } catch { }

    Unregister-Event -SubscriptionId $eventSubscriber.Id -ErrorAction SilentlyContinue
    
    $linesToSave = @($logList)

    # Save log file
    if ($linesToSave.Count -gt 0) {
        # Auto-trim to latest 1000 lines if exceeding 1000
        if ($linesToSave.Count -gt 1000) {
            $linesToSave = $linesToSave[($linesToSave.Count - 1000)..($linesToSave.Count - 1)]
            Write-Host "`n* Automatically trimmed to the latest 1000 lines for log management." -ForegroundColor Yellow
        }

        $totalLines = $linesToSave.Count
        Write-Host "`nSaving log file to disk... Total lines: $totalLines" -ForegroundColor Yellow
        Write-Host "Full save path: $saveFullPath" -ForegroundColor Cyan
            
        $stream = [System.IO.File]::Create($saveFullPath)
        $writer = New-Object System.IO.StreamWriter($stream, [System.Text.Encoding]::UTF8, 65536)
        
        function Sanitize-LogLine([string]$l) {
            if (-not $l) { return "" }
            $l = $l -replace '(\d{2,4}-\d{2,4}-\d{4}|\d{10,11})', '[PHONE NUMBER MASKED]'
            $l = $l -replace '(Speaking:\s*(通知:\s*)?)(.+?)(さん|氏|様)', '$1[NAME MASKED]$4'
            return $l
        }

        try {
            $batchSize = [Math]::Max([int]($totalLines / 10), 1)
            for ($i = 0; $i -lt $totalLines; $i++) {
                $sanitizedLine = Sanitize-LogLine $linesToSave[$i]
                $writer.WriteLine($sanitizedLine)
                
                if ($i -gt 0 -and ((($i % $batchSize) -eq 0) -or ($i -eq ($totalLines - 1)))) {
                    $percent = [int]((($i + 1) / $totalLines) * 100)
                    Write-Host "Saving progress: $percent% ($($i + 1) / $totalLines lines)" -ForegroundColor Cyan
                }
            }
        }
        finally {
            $writer.Flush()
            $writer.Close()
            $stream.Close()
        }

        # Happy beep on completion
        try {
            [Console]::Beep(800, 150)
        } catch { }

        Write-Host "Log collection finished!" -ForegroundColor Yellow
        Write-Host "Log file saved successfully: [ $saveFullPath ] (Total lines: $totalLines)" -ForegroundColor Green
    }
    else {
        Write-Host "`nLog collection finished. 0 log entries were captured." -ForegroundColor Red
        Write-Host "Target save path was: $saveFullPath" -ForegroundColor Yellow
    }
}