package com.stomato.helloreader

/**
 * 日本語数字通話表（無線局運用規則 別表第5号 通話表）および
 * 英語数字フォネティック対応表
 *
 * なでしこ3公式仕様書:
 * - Japanese_Phonetic_Braille_Feedback.nako3
 * - Gboard_Touch_Exploration_Braille.nako3
 * に完全準拠。
 */
object JapanesePhoneticTable {

    /**
     * 無線局運用規則 別表第5号「数字通話表」
     * 半角数字 ('0'..'9')、全角数字 ('０'..'９')、および漢数字 ('〇'..'九')
     */
    val JAPANESE_DIGIT_MAP: Map<Char, String> = mapOf(
        '0' to "数字のまる", '〇' to "数字のまる", '０' to "数字のまる",
        '1' to "数字のひと", '一' to "数字のひと", '１' to "数字のひと",
        '2' to "数字のふた", '二' to "数字のふた", '２' to "数字のふた",
        '3' to "数字のさん", '三' to "数字のさん", '３' to "数字のさん",
        '4' to "数字のよん", '四' to "数字のよん", '４' to "数字のよん",
        '5' to "数字のご",   '五' to "数字のご",   '５' to "数字のご",
        '6' to "数字のろく", '六' to "数字のろく", '６' to "数字のろく",
        '7' to "数字のなな", '七' to "数字のなな", '７' to "数字のなな",
        '8' to "数字のはち", '八' to "数字のはち", '８' to "数字のはち",
        '9' to "数字のきゅう", '九' to "数字のきゅう", '９' to "数字のきゅう"
    )

    /**
     * 英語数字フォネティック辞書（半角・全角）
     */
    val ENGLISH_DIGIT_MAP: Map<Char, String> = mapOf(
        '0' to "ゼロ",     '1' to "ワン",     '2' to "ツー",     '3' to "スリー",   '4' to "フォー",
        '5' to "ファイブ", '6' to "シックス", '7' to "セブン",   '8' to "エイト",   '9' to "ナイン",
        '０' to "ゼロ",    '１' to "ワン",     '２' to "ツー",    '３' to "スリー",   '４' to "フォー",
        '５' to "ファイブ", '６' to "シックス", '７' to "セブン",  '８' to "エイト",   '９' to "ナイン"
    )

    /**
     * 対象文字が数字（半角数字、全角数字、漢数字）であるかを判定
     */
    fun isDigit(ch: Char): Boolean {
        return ch in '0'..'9' || ch in '０'..'９' || JAPANESE_DIGIT_MAP.containsKey(ch)
    }

    /**
     * 日本語通話表（無線局運用規則）に基づく数字の読みを取得
     */
    fun getJapaneseDigitPhonetic(ch: Char): String? {
        return JAPANESE_DIGIT_MAP[ch]
    }

    /**
     * 英語フォネティックに基づく数字の読みを取得
     */
    fun getEnglishDigitPhonetic(ch: Char): String? {
        return ENGLISH_DIGIT_MAP[ch]
    }

    /**
     * キーボード言語モードに応じて適切な数字フォネティック読みを解決
     */
    fun resolveDigitPhonetic(ch: Char, isEnglishKeyboard: Boolean): String? {
        return if (isEnglishKeyboard) {
            getEnglishDigitPhonetic(ch)
        } else {
            getJapaneseDigitPhonetic(ch)
        }
    }
}
