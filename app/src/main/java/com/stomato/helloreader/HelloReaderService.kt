package com.stomato.helloreader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Hello Reader Accessibility Service
 * 智浩さんの Hello reader 計画書に基づくメインサービス
 */
class HelloReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "HelloReaderService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Hello Reader Accessibility Service Connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 画面イベント受け取りログ
        val eventType = event.eventType
        Log.d(TAG, "onAccessibilityEvent: $eventType")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Hello Reader Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Hello Reader Service Destroyed")
    }
}
