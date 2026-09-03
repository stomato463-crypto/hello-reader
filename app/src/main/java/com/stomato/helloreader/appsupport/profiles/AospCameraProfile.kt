package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.stomato.helloreader.appsupport.AppProfile

/**
 * AOSP / 標準「カメラ」アプリ (com.android.camera2 / com.google.android.GoogleCamera) 用プロファイル
 */
class AospCameraProfile(override val targetPackageName: String = "com.android.camera2") : AppProfile {

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val viewId = compat.viewIdResourceName ?: ""

        // シャッターボタン
        if (viewId.contains("shutter_button") || viewId.contains("shutter")) {
            return Pair("シャッター", "ボタン。ダブルタップで撮影")
        }

        // カメラ切替ボタン（イン/アウト）
        if (viewId.contains("toggle") || viewId.contains("switch_camera") || viewId.contains("facing_switch")) {
            return Pair("カメラ切替", "ボタン。インカメラとアウトカメラを切り替え")
        }

        return null
    }
}
