package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「時計」アプリ (com.android.deskclock / com.google.android.deskclock) 用プロファイル
 */
class AospClockProfile(override val targetPackageName: String = "com.android.deskclock") : AppProfile {

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val viewId = compat.viewIdResourceName ?: ""

        // アラームOnOffスイッチ
        if (viewId.contains("onoff") || viewId.contains("alarm_onoff")) {
            val state = if (compat.isChecked) "有効" else "無効"
            return Pair("アラーム $state", "ダブルタップで切り替え")
        }

        // アラーム追加ボタン
        if (viewId.contains("fab") || viewId.contains("add_alarm")) {
            return Pair("アラームを追加", "ボタン。ダブルタップで設定")
        }

        return null
    }
}
