package com.stomato.helloreader.appsupport

import android.util.Log

/**
 * 無名ボタン等の View ID やリソース名に対してカスタムラベル（音声代替テキスト）を提供するマネージャー
 */
class CustomLabelManager {

    companion object {
        private const val TAG = "CustomLabelManager"
    }

    // パッケージ名 + ":" + viewId -> ラベル文字列 のプリセット辞書
    private val customLabelsMap = mutableMapOf<String, String>()

    init {
        // 標準の有名アプリ用のプリセット・カスタムラベル登録
        registerDefaultCustomLabels()
    }

    /**
     * 標準のプリセットカスタムラベルを登録する
     */
    private fun registerDefaultCustomLabels() {
        // 例: 一般的なシステムUIや標準アプリの無名アイコン用ラベル
        customLabelsMap["com.android.systemui:id/back"] = "戻るボタン"
        customLabelsMap["com.android.systemui:id/home"] = "ホームボタン"
        customLabelsMap["com.android.systemui:id/recent_apps"] = "最近のアプリボタン"
    }

    /**
     * 指定されたパッケージ名と View ID に対応するカスタムラベルを取得する
     */
    fun getCustomLabel(packageName: String?, viewIdResourceName: String?): String? {
        if (packageName.isNullOrBlank() || viewIdResourceName.isNullOrBlank()) return null
        val key = "$packageName:$viewIdResourceName"
        val label = customLabelsMap[key]
        if (label != null) {
            Log.d(TAG, "Custom Label found for [$key]: $label")
        }
        return label
    }

    /**
     * 動的にカスタムラベルを追加・登録する
     */
    fun setCustomLabel(packageName: String, viewIdResourceName: String, label: String) {
        val key = "$packageName:$viewIdResourceName"
        customLabelsMap[key] = label
        Log.d(TAG, "Custom Label set: [$key] -> $label")
    }
}
