package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「連絡先」アプリ (com.android.contacts / com.google.android.contacts) 用プロファイル
 */
class AospContactsProfile(override val targetPackageName: String = "com.android.contacts") : AppProfile {

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val viewId = compat.viewIdResourceName ?: ""

        // 連絡先追加ボタン
        if (viewId.contains("floating_action_button") || viewId.contains("add_contact")) {
            return Pair("連絡先を追加", "ボタン。ダブルタップで新規連絡先登録")
        }

        // 検索ボタン・バー
        if (viewId.contains("search")) {
            return Pair("連絡先を検索", "タップして名前や電話番号を検索")
        }

        return null
    }
}
