package com.stomato.helloreader.appsupport.profiles

import android.view.accessibility.AccessibilityNodeInfo
import com.stomato.helloreader.appsupport.AppProfile

/**
 * Googleキーボード (Gboard: com.google.android.inputmethod.latin) 専用読み上げプロファイル
 * Gboard固有のノード構造 (「a ALPHA」等のハードコード属性) やモード切替キーを完全分離モジュール化
 */
class GboardProfile : AppProfile {

    override val targetPackageName: String = "com.google.android.inputmethod.latin"

    private val natoPhoneticMap = mapOf(
        'A' to "アルファ", 'B' to "ブラボー", 'C' to "チャーリー", 'D' to "デルタ",
        'E' to "エコー", 'F' to "フォックストロット", 'G' to "ゴルフ", 'H' to "ホテル",
        'I' to "インド", 'J' to "ジュリエット", 'K' to "キロ", 'L' to "リマ",
        'M' to "マイク", 'N' to "ノーベンバー", 'O' to "オスカー", 'P' to "パパ",
        'Q' to "ケベック", 'R' to "ロメオ", 'S' to "シエラ", 'T' to "タンゴ",
        'U' to "ユニフォーム", 'V' to "ビクター", 'W' to "ウイスキー", 'X' to "エックスレイ",
        'Y' to "ヤンキー", 'Z' to "ズールー"
    )

    override fun customizeNodeDescription(
        node: AccessibilityNodeInfo,
        rawMainText: String?,
        rawHintText: String?
    ): Pair<String, String?>? {
        val text = rawMainText?.trim() ?: return null
        if (text.isBlank()) return null

        // 1. Gboard 複合キーノード (例: "a ALPHA", "b BRAVO", "s SIERRA") の分離解読
        val parts = text.split("\\s+".toRegex())
        if (parts.size >= 2 && parts[0].length == 1) {
            val singleChar = parts[0][0]
            val rawPhonetic = parts.subList(1, parts.size).joinToString(" ")
            val upperChar = singleChar.uppercaseChar()
            val phonetic = natoPhoneticMap[upperChar] ?: rawPhonetic
            return Pair(singleChar.toString(), phonetic)
        }

        // 2. Gboard 単一文字キー ('a', 'b', 's' 等)
        if (text.length == 1) {
            val ch = text[0]
            val upperChar = ch.uppercaseChar()
            val phonetic = natoPhoneticMap[upperChar]
            if (phonetic != null) {
                return Pair(ch.toString(), phonetic)
            }
        }

        // 3. Gboard モード切替・地球儀キー・特殊キーの救済
        if (text.contains("言語") || text.contains("キーボード") || text.contains("入力方法")) {
            return Pair(text, "ダブルタップで入力言語を切り替えます")
        }

        return null
    }
}

