package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import com.stomato.helloreader.appsupport.AppProfile

/**
 * Googleキーボード (Gboard: com.google.android.inputmethod.latin) 専用読み上げプロファイル
 * Gboard固有のノード構造 (「a ALPHA」等のハードコード属性) やモード切替キーを完全分離モジュール化
 */
class GboardProfile : AppProfile {

    override val targetPackageName: String = "com.google.android.inputmethod.latin"

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val text = rawMainText?.trim() ?: return null
        if (text.isBlank()) return null

        // 1. Gboard 複合キーノード (例: "a ALPHA", "b BRAVO", "a アルファ", "あ あさひのあ")
        // および単一文字キーの分離解読
        // ※重要: フォネティックテキスト ("ALPHA") を破棄せず、キー文字とフォネティックヒントの両方を保持して返却する！
        val parsedKey = parseKeyText(text, rawHintText)
        if (parsedKey != null) {
            return parsedKey
        }

        // 2. Gboard スペースキーノード (key_pos_space または "English", "Space", "日本語", "空白" 等)
        if (isSpaceKey(node, text)) {
            val mainText = if (text.equals("space", ignoreCase = true) || text.contains("English", ignoreCase = true) || text.contains("日本語") || text.contains("空白")) {
                "スペース"
            } else {
                text
            }
            return Pair(mainText, text)
        }

        // 3. Gboard モード切替・地球儀キー・特殊キーの救済
        if (text.contains("言語") || text.contains("キーボード") || text.contains("入力方法")) {
            return Pair(text, "ダブルタップで入力言語を切り替えます")
        }

        return null
    }

    companion object {
        /**
         * 複合キーまたは単一文字テキストを解析し、Pair(文字, フォネティックヒント)を生成
         */
        fun parseKeyText(text: String?, defaultHint: String? = null): Pair<String, String?>? {
            val trimmed = text?.trim() ?: return null
            if (trimmed.isBlank()) return null

            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size >= 2 && parts[0].length == 1) {
                val singleChar = parts[0][0]
                val rawPhonetic = parts.subList(1, parts.size).joinToString(" ")
                return Pair(singleChar.toString(), rawPhonetic)
            }

            if (trimmed.length == 1) {
                val singleChar = trimmed[0]
                return Pair(singleChar.toString(), defaultHint)
            }

            return null
        }
        /**
         * スペースキーのテキストから言語モード ("en" または "ja") を動的判定
         * (なでしこ3仕様書: Gboard_Touch_Exploration_Braille.nako3 Section 3 準拠)
         */
        fun detectLanguageFromSpaceKey(text: CharSequence?): String? {
            if (text.isNullOrBlank()) return null
            val trimmed = text.toString().trim()

            // 1. 英語モード判定パターン
            // スペースキーに "English", "English (US)", "英語", "Space", "space", "QWERTY", "Alphabet" が含まれる場合
            if (trimmed.contains("English", ignoreCase = true) ||
                trimmed.contains("英語") ||
                trimmed.equals("Space", ignoreCase = true) ||
                trimmed.equals("space", ignoreCase = true) ||
                trimmed.contains("QWERTY", ignoreCase = true) ||
                trimmed.contains("Alphabet", ignoreCase = true)
            ) {
                return "en"
            }

            // 2. 日本語モード判定パターン
            // スペースキーに "日本語", "Japanese", "空白", "かな", "フリック", "ローマ字", "12キー", "テンキー" が含まれる場合
            if (trimmed.contains("日本語") ||
                trimmed.contains("Japanese", ignoreCase = true) ||
                trimmed.contains("空白") ||
                trimmed.contains("かな") ||
                trimmed.contains("フリック") ||
                trimmed.contains("ローマ字") ||
                trimmed.contains("12キー") ||
                trimmed.contains("テンキー")
            ) {
                return "ja"
            }

            return null
        }

        /**
         * 対象ノードまたはテキストがスペースキーであるかを判定
         */
        fun isSpaceKey(node: AccessibilityNodeInfo?, text: CharSequence? = null): Boolean {
            val resId = node?.viewIdResourceName ?: ""
            if (resId.contains("key_pos_space", ignoreCase = true)) {
                return true
            }
            val textStr = (text ?: node?.text ?: node?.contentDescription)?.toString()?.trim() ?: ""
            return textStr.equals("space", ignoreCase = true) ||
                    textStr.contains("スペース") ||
                    textStr.contains("空白") ||
                    textStr.contains("English", ignoreCase = true) ||
                    (textStr.contains("日本語") && resId.contains("space", ignoreCase = true))
        }

        /**
         * ノード単体から言語モード ("en" / "ja") を判定
         */
        fun detectLanguageFromNode(node: AccessibilityNodeInfo?): String? {
            if (node == null) return null
            val resId = node.viewIdResourceName ?: ""
            val nodeText = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            if (resId.contains("key_pos_space", ignoreCase = true) || isSpaceKey(node, nodeText)) {
                val detected = detectLanguageFromSpaceKey(nodeText)
                if (detected != null) return detected
            }
            // 日本語固有キーノード ("あ", "か", "確定", "変換")
            val text = node.text?.toString() ?: ""
            if (text == "あ" || text == "か" || text == "確定" || text == "変換") {
                return "ja"
            }
            return null
        }
    }
}

