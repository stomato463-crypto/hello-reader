package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「設定」アプリ (com.android.settings) 用プロファイル
 */
@Suppress("DEPRECATION")
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
            val text = extractSettingsTitle(node) ?: rawMainText ?: "設定項目"
            return Pair(text, "スイッチ $stateText。ダブルタップで切り替え")
        }

        // rawMainText が空の場合、子ノードから設定のタイトルとサブタイトル（Summary）を直接探索・合成
        if (rawMainText.isNullOrBlank()) {
            val extractedText = extractSettingsTitle(node)
            if (!extractedText.isNullOrBlank()) {
                val hint = if (compat.isClickable) "ダブルタップで開く" else null
                return Pair(extractedText, hint)
            }
        }

        return null
    }

    private fun extractSettingsTitle(node: AccessibilityNodeInfo): String? {
        val titles = mutableListOf<String>()
        collectChildTexts(node, titles)
        return if (titles.isNotEmpty()) titles.joinToString("。") else null
    }

    private fun collectChildTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank() && !list.contains(text.trim())) {
            list.add(text.trim())
        }
        for (i in 0 until node.childCount) {
            collectChildTexts(node.getChild(i), list)
        }
    }
}
