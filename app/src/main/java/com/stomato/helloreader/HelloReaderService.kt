package com.stomato.helloreader

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import java.util.Locale

/**
 * Hello Reader Accessibility Service
 * 智浩さんの Hello reader 計画書に基づくメインサービス
 */
class HelloReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "HelloReaderService"
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Hello Reader Accessibility Service Connected!")
        // 声帯 (TTS: 音声合成) の初期化
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "日本語のTTSデータが見つからないか対応していないにょろ")
            } else {
                isTtsReady = true
                speak("Hello Readerが起動したにょろ")
            }
        } else {
            Log.e(TAG, "TTSの初期化に失敗したにょろ")
        }
    }

    /**
     * ジェスチャー検知 (API 30+ 互換対応)
     */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        val gestureId = gestureEvent.gestureId
        handleGesture(gestureId)
        return true
    }

    /**
     * ジェスチャー検知 (API 30未満 フォールバック対応)
     */
    @Deprecated("Deprecated in API 30")
    override fun onGesture(gestureId: Int): Boolean {
        handleGesture(gestureId)
        return true
    }

    /**
     * 検知したジェスチャーの解析とフィードバック
     */
    private fun handleGesture(gestureId: Int) {
        val gestureName = when (gestureId) {
            GESTURE_SWIPE_RIGHT -> "右スワイプ"
            GESTURE_SWIPE_LEFT -> "左スワイプ"
            GESTURE_SWIPE_UP -> "上スワイプ"
            GESTURE_SWIPE_DOWN -> "下スワイプ"
            GESTURE_SWIPE_UP_AND_DOWN -> "上下スワイプ"
            GESTURE_SWIPE_DOWN_AND_UP -> "下上スワイプ"
            GESTURE_DOUBLE_TAP -> "ダブルタップ"
            else -> "ジェスチャー $gestureId"
        }
        Log.d(TAG, "Gesture Detected: $gestureName ($gestureId)")
        speak("$gestureName を検知したにょろ")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isTtsReady) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val text = getEventText(event)
                if (!text.isNullOrBlank()) {
                    speak(text)
                }
            }
        }
    }

    private fun getEventText(event: AccessibilityEvent): String? {
        val sb = StringBuilder()

        // 1. イベント自体のテキスト
        if (event.text.isNotEmpty()) {
            for (t in event.text) {
                if (!t.isNullOrBlank()) {
                    sb.append(t).append(" ")
                }
            }
        }

        // 2. contentDescription (説明文)
        if (sb.isEmpty() && !event.contentDescription.isNullOrBlank()) {
            sb.append(event.contentDescription)
        }

        // 3. アクセシビリティノードのテキスト
        if (sb.isEmpty()) {
            val node = event.source
            if (node != null && !node.text.isNullOrBlank()) {
                sb.append(node.text)
            }
        }

        return if (sb.isNotEmpty()) sb.toString().trim() else null
    }

    private fun speak(text: String) {
        if (!isTtsReady) return
        Log.d(TAG, "Speaking: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HelloReaderTTS")
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
