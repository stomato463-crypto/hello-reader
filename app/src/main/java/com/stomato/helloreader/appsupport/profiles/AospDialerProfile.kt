package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「電話」アプリ (com.android.dialer / com.google.android.dialer) 用プロファイル
 */
class AospDialerProfile(override val targetPackageName: String = "com.android.dialer") : AppProfile {

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val viewId = compat.viewIdResourceName ?: ""

        // ダイヤルパッドボタン
        if (viewId.contains("dialpad") || viewId.contains("one") || viewId.contains("two") || viewId.contains("three")) {
            val digitText = rawMainText ?: ""
            return Pair(digitText, "ダイヤルキー")
        }

        // 発信ボタン・通話切断ボタン
        if (viewId.contains("fab") || viewId.contains("call") || viewId.contains("dial")) {
            val actionText = rawMainText ?: "発信"
            return Pair(actionText, "ボタン。ダブルタップで発信")
        }

        return null
    }
}
