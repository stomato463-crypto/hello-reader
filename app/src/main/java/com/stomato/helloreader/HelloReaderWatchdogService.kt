package com.stomato.helloreader

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 🌸 緊急時の音声復帰ウォッチドッグサービス (独立プロセス :watchdog で動作)
 * HelloReaderService からの心拍 (Heartbeat) を監視し、1分間反応がない場合は
 * 完全フリーズとみなして「R」のモールス信号振動(3回)とTalkBack切り替え案内を行います。
 */
class HelloReaderWatchdogService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "HelloReaderWatchdog"
        const val ACTION_START_WATCHDOG = "com.stomato.helloreader.ACTION_START_WATCHDOG"
        const val ACTION_HEARTBEAT = "com.stomato.helloreader.ACTION_HEARTBEAT"
        const val ACTION_STOP_WATCHDOG = "com.stomato.helloreader.ACTION_STOP_WATCHDOG"

        private const val TIMEOUT_MS = 60_000L // 1分間無応答でフリーズ判定
        private const val CHECK_INTERVAL_MS = 5_000L // 5秒ごとにチェック

        const val EMERGENCY_SPEECH_TEXT =
            "音声読み上げのトラブルを検知したため、緊急復帰案内を開始しました。画面が見えなくなったり操作に迷った時は、スマートフォンの音量ボタンの上と下を同時に約3秒間長押ししてください。いつでも標準のTalkBackに切り替えられます。"
    }

    private var lastHeartbeatTime: Long = 0L
    private var isMonitoring: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isTtsReady: Boolean = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return

            val elapsed = System.currentTimeMillis() - lastHeartbeatTime
            if (elapsed >= TIMEOUT_MS) {
                if (isKeyguardLockedAndMainProcessAlive()) {
                    Log.d(TAG, "[$TIMEOUT_MS ms] 心拍途絶を検知しましたが、画面ロック中でメインプロセスが生存しているため緊急復帰案内を抑止します。")
                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                } else {
                    Log.e(TAG, "[$TIMEOUT_MS ms] メインサービスからの心拍が途絶えました。完全フリーズと判断し緊急復帰処理を開始します。")
                    triggerEmergencyRecovery()
                    isMonitoring = false // 連打防止のため監視を一時停止
                }
            } else {
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * 🌸 画面ロック状態かつメインプロセス生存の判定
     * もし、画面ロック状態かつ HelloReaderService（メインプロセス）が生存しているならば、
     * 画面ロック状態での誤発動を防止するため true を返します。
     */
    private fun isKeyguardLockedAndMainProcessAlive(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true

        if (!isLocked) {
            return false
        }

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val runningProcesses = activityManager?.runningAppProcesses
        return runningProcesses?.any { it.processName == packageName } == true
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WatchdogService created (Process: :watchdog)")
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                Log.d(TAG, "Watchdog TTS initialized successfully")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WATCHDOG -> {
                Log.d(TAG, "Watchdog monitoring started")
                lastHeartbeatTime = System.currentTimeMillis()
                if (!isMonitoring) {
                    isMonitoring = true
                    handler.removeCallbacks(checkRunnable)
                    handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
                }
            }
            ACTION_HEARTBEAT -> {
                lastHeartbeatTime = System.currentTimeMillis()
                // もし一時停止していた場合は再開
                if (!isMonitoring) {
                    isMonitoring = true
                    handler.removeCallbacks(checkRunnable)
                    handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
                }
            }
            ACTION_STOP_WATCHDOG -> {
                Log.d(TAG, "Watchdog monitoring stopped")
                isMonitoring = false
                handler.removeCallbacks(checkRunnable)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 🌸 緊急復帰処理
     * 1. Rのモールス信号（短・長・短）を 3回 振動だけで実行
     * 2. アクセシビリティ設定画面の起動
     * 3. 復帰案内メッセージの発声
     */
    private fun triggerEmergencyRecovery() {
        // 1. Rのモールス信号（短・長・短）を3回 振動実行
        playMorseRThreeTimes()

        // 2. Accessibility Settings 起動
        try {
            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(settingsIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
        }

        // 3. 復帰案内メッセージの発声
        if (isTtsReady) {
            tts?.speak(EMERGENCY_SPEECH_TEXT, TextToSpeech.QUEUE_FLUSH, null, "WatchdogEmergencyTTS")
        }
    }

    /**
     * モールス信号 「R」（・ー・）を 3回 振動だけで実行
     */
    private fun playMorseRThreeTimes() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator?.hasVibrator() == true) {
            // R (.-.) 3回: 短 150ms, 符間 100ms, 長 450ms, 符間 100ms, 短 150ms, 語間 500ms
            val pattern = longArrayOf(
                0,
                150, 100, 450, 100, 150, 500,
                150, 100, 450, 100, 150, 500,
                150, 100, 450, 100, 150
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "WatchdogService destroyed")
        isMonitoring = false
        handler.removeCallbacks(checkRunnable)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) { }
        super.onDestroy()
    }
}
