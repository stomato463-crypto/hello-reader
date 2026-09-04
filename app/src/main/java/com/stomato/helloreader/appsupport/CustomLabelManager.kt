package com.stomato.helloreader.appsupport

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 無名ボタン等の View ID カスタムラベル ＆ ユーザー定義・読み間違い修正辞書（Pronunciation Dictionary）マネージャー
 */
class CustomLabelManager(private val context: Context? = null) {

    companion object {
        private const val TAG = "CustomLabelManager"
        private const val PREFS_NAME = "hello_reader_prefs"
        private const val KEY_PRONUNCIATION_DICT = "key_user_pronunciation_dict"
    }

    // 1. パッケージ名 + ":" + viewId -> ラベル文字列 のプリセット辞書
    private val customLabelsMap = mutableMapOf<String, String>()

    // 2. 単語読み替え辞書 (元の表記 -> 読み上げテキスト)
    private val pronunciationDict = mutableMapOf<String, String>()

    init {
        registerDefaultCustomLabels()
        loadUserPronunciations()
    }

    private fun registerDefaultCustomLabels() {
        customLabelsMap["com.android.systemui:id/back"] = "戻るボタン"
        customLabelsMap["com.android.systemui:id/home"] = "ホームボタン"
        customLabelsMap["com.android.systemui:id/recent_apps"] = "最近のアプリボタン"
    }

    private fun loadUserPronunciations() {
        // デフォルトプリセット読み替え
        pronunciationDict["100円"] = "ひゃくえん"
        pronunciationDict["1日"] = "ついたち"
        pronunciationDict["2日"] = "ふつか"
        pronunciationDict["3日"] = "みっか"
        pronunciationDict["4日"] = "よっか"
        pronunciationDict["20日"] = "はつか"
        pronunciationDict["WiFi"] = "ワイファイ"
        pronunciationDict["Wi-Fi"] = "ワイファイ"
        pronunciationDict["Bluetooth"] = "ブルートゥース"

        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PRONUNCIATION_DICT, null)
            if (!jsonStr.isNullOrBlank()) {
                val json = JSONObject(jsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    pronunciationDict[key] = json.getString(key)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user pronunciation dict", e)
        }
    }

    private fun saveUserPronunciations() {
        if (context == null) return
        try {
            val json = JSONObject()
            pronunciationDict.forEach { (k, v) ->
                json.put(k, v)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PRONUNCIATION_DICT, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user pronunciation dict", e)
        }
    }

    /**
     * テキスト内の登録単語を読み上げ用テキストに全置換する
     */
    fun replacePronunciations(text: String): String {
        if (text.isBlank() || pronunciationDict.isEmpty()) return text
        var result = text
        pronunciationDict.forEach { (original, reading) ->
            if (result.contains(original)) {
                result = result.replace(original, reading)
            }
        }
        return result
    }

    fun getAllPronunciations(): Map<String, String> {
        return pronunciationDict.toMap()
    }

    fun addPronunciation(original: String, reading: String) {
        if (original.isBlank() || reading.isBlank()) return
        pronunciationDict[original] = reading
        saveUserPronunciations()
        Log.d(TAG, "Added pronunciation: [$original] -> [$reading]")
    }

    fun removePronunciation(original: String) {
        pronunciationDict.remove(original)
        saveUserPronunciations()
        Log.d(TAG, "Removed pronunciation for [$original]")
    }

    fun getCustomLabel(packageName: String?, viewIdResourceName: String?): String? {
        if (packageName.isNullOrBlank() || viewIdResourceName.isNullOrBlank()) return null
        val key = "$packageName:$viewIdResourceName"
        return customLabelsMap[key]
    }

    fun setCustomLabel(packageName: String, viewIdResourceName: String, label: String) {
        val key = "$packageName:$viewIdResourceName"
        customLabelsMap[key] = label
    }
}
