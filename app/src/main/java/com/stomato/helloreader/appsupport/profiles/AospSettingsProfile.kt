package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「設定」アプリ (com.android.settings) 用プロファイル
 */
class AospSettingsProfile : AppProfile {
    override val targetPackageName: String = "com.android.settings"

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val viewId = compat.viewIdResourceName ?: ""

        // 設定画面の検索バー
        if (viewId.contains("search") || viewId.contains("search_action_bar")) {
            return Pair(rawMainText ?: "設定の検索", "タップして設定項目を検索します")
        }

        // トグルスイッチやカテゴリ項目の強調
        if (compat.isCheckable) {
            val stateText = if (compat.isChecked) "オン" else "オフ"
            val text = rawMainText ?: "設定項目"
            return Pair(text, "スイッチ $stateText。ダブルタップで切り替え")
        }

        return null
    }
}
