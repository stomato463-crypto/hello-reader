package com.stomato.helloreader

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * スマート時報 ＆ バッテリー残量アナウンスマネージャー
 */
class TimeSignalManager(
    private val context: Context,
    private val onTimeSignal: (timeText: String, batteryText: String, isSilent: Boolean) -> Unit
) {

    companion object {
        private const val TAG = "TimeSignalManager"
        private const val PREFS_NAME = "hello_reader_prefs"
        const val PREF_TIME_SIGNAL_ENABLED = "pref_time_signal_enabled"
        const val PREF_TIME_SIGNAL_INTERVAL = "pref_time_signal_interval" // "HOURLY" or "HALF_HOURLY"
        const val PREF_TIME_SIGNAL_NIGHT_QUIET = "pref_time_signal_night_quiet" // 23:00 - 07:00
    }

    private var isReceiverRegistered = false
    private var lastAnnouncedHour = -1
    private var lastAnnouncedMinute = -1

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                checkAndAnnounceTimeSignal()
            }
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(Intent.ACTION_TIME_TICK)
                context.registerReceiver(timeTickReceiver, filter)
                isReceiverRegistered = true
                Log.d(TAG, "TimeSignalManager started.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register TIME_TICK receiver", e)
            }
        }
    }

    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(timeTickReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "TimeSignalManager stopped.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister TIME_TICK receiver", e)
            }
        }
    }

    private fun checkAndAnnounceTimeSignal() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(PREF_TIME_SIGNAL_ENABLED, true)
        if (!isEnabled) return

        val interval = prefs.getString(PREF_TIME_SIGNAL_INTERVAL, "HOURLY") ?: "HOURLY"
        val nightQuiet = prefs.getBoolean(PREF_TIME_SIGNAL_NIGHT_QUIET, true)

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val isTargetMinute = when (interval) {
            "HALF_HOURLY" -> (minute == 0 || minute == 30)
            else -> (minute == 0)
        }

        if (!isTargetMinute) return
        if (hour == lastAnnouncedHour && minute == lastAnnouncedMinute) return

        lastAnnouncedHour = hour
        lastAnnouncedMinute = minute

        // おやすみモード（夜間 23:00〜07:00）またはOSのDo Not Disturb状態
        val isNightTime = (hour >= 23 || hour < 7)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val isDndActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } else false

        val isSilent = (nightQuiet && isNightTime) || isDndActive

        // バッテリー残量の取得
        val batteryPct = getBatteryPercentage()
        val timeString = if (minute == 0) {
            "${hour}時ちょうどです"
        } else {
            "${hour}時${minute}分です"
        }
        val batteryString = "バッテリー残量${batteryPct}パーセント"

        Log.d(TAG, "Triggering TimeSignal: $timeString, $batteryString, isSilent=$isSilent")
        onTimeSignal(timeString, batteryString, isSilent)
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
        }
    }
}
