package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import com.stomato.helloreader.appsupport.AppProfile

/**
 * デフォルトの汎用アプリプロファイル
 */
class DefaultAppProfile : AppProfile {
    override val targetPackageName: String = "*"

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        // 標準アプリプロファイルでは特別な上書きを行わずにそのまま返却
        return null
    }
}
