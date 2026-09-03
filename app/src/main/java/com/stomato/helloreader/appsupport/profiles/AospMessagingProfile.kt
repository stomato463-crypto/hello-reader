package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「メッセージ」アプリ (com.android.messaging / com.google.android.apps.messaging) 用プロファイル
 */
class AospMessagingProfile(override val targetPackageName: String = "com.android.messaging") : AppProfile {

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val viewId = compat.viewIdResourceName ?: ""

        // メッセージ送信ボタン
        if (viewId.contains("send_message_button") || viewId.contains("send_button")) {
            return Pair("メッセージ送信", "ボタン。ダブルタップで送信")
        }

        // 新規作成ボタン
        if (viewId.contains("start_chat") || viewId.contains("create_conversation")) {
            return Pair("新しいメッセージを作成", "ボタン。ダブルタップでチャット作成")
        }

        return null
    }
}
