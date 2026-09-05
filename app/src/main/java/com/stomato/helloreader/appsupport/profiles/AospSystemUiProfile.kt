package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import com.stomato.helloreader.appsupport.AppProfile

/**
 * Android AOSP / Pixel SystemUI (ステータスバー・クイック設定・通知・ロック画面) 専用プロファイル
 */
@Suppress("DEPRECATION")
class AospSystemUiProfile : AppProfile {

    override val targetPackageName: String = "com.android.systemui"

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val resId = node.viewIdResourceName ?: ""
        val contentDesc = node.contentDescription?.toString()
        val text = node.text?.toString()

        // 1. バッテリー残量・充電状態アイコン
        if (resId.contains("battery") || contentDesc?.contains("バッテリー") == true || contentDesc?.contains("Battery") == true) {
            val desc = contentDesc ?: text ?: "バッテリー"
            val stateHint = if (node.isSelected || node.isChecked) "充電中" else null
            return Pair(desc, stateHint ?: rawHintText)
        }

        // 2. Wi-Fi / モバイルデータ電波アイコン
        if (resId.contains("wifi") || resId.contains("mobile") || resId.contains("signal")) {
            val desc = contentDesc ?: text ?: "電波状態"
            return Pair(desc, "ステータスバー")
        }

        // 3. クイック設定タイル (Quick Settings Tile)
        if (resId.contains("tile") || resId.contains("qs_")) {
            val tileName = contentDesc ?: text ?: "設定タイル"
            val state = when {
                node.isChecked || node.isSelected -> "有効"
                else -> "無効"
            }
            return Pair("$tileName、$state", "ダブルタップで切り替え")
        }

        // 4. 指紋センサー / ロック画面
        if (resId.contains("fingerprint") || contentDesc?.contains("指紋") == true) {
            return Pair("画面内指紋認証センサー", "指を長押ししてロック解除")
        }

        // 5. 時計
        if (resId.contains("clock") || resId.contains("time_view")) {
            val timeText = text ?: contentDesc ?: ""
            if (timeText.isNotEmpty()) {
                return Pair("現在時刻、$timeText", null)
            }
        }

        return null
    }
}
