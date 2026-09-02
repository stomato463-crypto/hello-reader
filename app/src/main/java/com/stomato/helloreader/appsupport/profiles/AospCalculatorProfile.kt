package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「電卓」アプリ (com.android.calculator2 / com.google.android.calculator) 用プロファイル
 */
class AospCalculatorProfile(override val targetPackageName: String = "com.android.calculator2") : AppProfile {

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val viewId = compat.viewIdResourceName ?: ""

        if (viewId.contains("op_equals") || rawMainText == "=") {
            return Pair("イコール（計算実行）", "ボタン")
        }

        if (viewId.contains("formula") || viewId.contains("result")) {
            val text = rawMainText ?: ""
            return Pair("計算結果 $text", null)
        }

        return null
    }
}
