package com.stomato.helloreader.appsupport

import android.view.accessibility.AccessibilityNodeInfo

/**
 * アプリごとの固有読み上げルール・補正を定義するインターフェース
 */
interface AppProfile {
    /**
     * 対応するアプリのパッケージ名（または識別用プレフィックス）
     */
    val targetPackageName: String

    /**
     * アプリ固有のノード解読・補正処理を行う
     * @param node 対象の AccessibilityNodeInfo
     * @param rawMainText 元のメインテキスト
     * @param rawHintText 元のヒントテキスト
     * @return 補正後の Pair(mainText, hintText)。補正を行わない場合は null
     */
    fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>?
}
