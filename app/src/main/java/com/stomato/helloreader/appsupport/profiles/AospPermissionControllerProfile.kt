package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import com.stomato.helloreader.appsupport.AppProfile

/**
 * Android 標準 権限許可ダイアログ (com.android.permissioncontroller / com.google.android.permissioncontroller) 専用プロファイル
 * 盲ろう・全盲ユーザーが迷わず確実に許可ボタンを押せるよう、ダイアログ出現時に要約とボタン位置を最適化
 */
class AospPermissionControllerProfile(override val targetPackageName: String = "com.google.android.permissioncontroller") : AppProfile {

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val resId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: rawMainText ?: ""

        // 1. 許可ボタン (Allow Button / Always Allow)
        if (resId.contains("permission_allow") || resId.contains("allow_button") || text.contains("許可") || text.contains("常に許可")) {
            return Pair("許可ボタン", "ダブルタップで権限を許可します")
        }

        // 2. 拒否ボタン (Deny Button / Don't Allow)
        if (resId.contains("permission_deny") || resId.contains("dont_allow") || text.contains("許可しない") || text.contains("拒否")) {
            return Pair("許可しないボタン", "ダブルタップで拒否します")
        }

        // 3. アプリ使用時のみ許可ボタン (While using the app)
        if (resId.contains("permission_allow_foreground_only") || text.contains("アプリの使用中のみ")) {
            return Pair("アプリ使用中のみ許可ボタン", "ダブルタップで選択します")
        }

        // 4. ダイアログのメッセージ本文
        if (resId.contains("permission_message") || resId.contains("desc")) {
            return Pair("権限の確認。$text", "スワイプして許可ボタンを選択してください")
        }

        return null
    }
}
