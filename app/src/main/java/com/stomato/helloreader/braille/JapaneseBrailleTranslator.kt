package com.stomato.helloreader.braille

import com.stomato.helloreader.braille.BrailleConstants.DOT_1
import com.stomato.helloreader.braille.BrailleConstants.DOT_2
import com.stomato.helloreader.braille.BrailleConstants.DOT_3
import com.stomato.helloreader.braille.BrailleConstants.DOT_4
import com.stomato.helloreader.braille.BrailleConstants.DOT_5
import com.stomato.helloreader.braille.BrailleConstants.DOT_6
import com.stomato.helloreader.braille.BrailleConstants.DOT_BLANK

/**
 * 日本語 6点/8点 点字翻訳エンジン (NABCC / 日本点字表記法準拠)
 * ひらがな・カタカナ・英数字・記号・UIロールを点字バイト配列に変換
 */
object JapaneseBrailleTranslator {

    // 特殊符
    private val PREFIX_NUMBER = (DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte() // 数符 (3-4-5-6)
    private val PREFIX_LATIN = (DOT_5.toInt() or DOT_6.toInt()).toByte() // 外国語符 (5-6)
    private val PREFIX_DAKUTEN = (DOT_5.toInt()).toByte() // 濁点符 (5)
    private val PREFIX_HANDAKUTEN = (DOT_6.toInt()).toByte() // 半濁点符 (6)
    private val PREFIX_YOUON = (DOT_4.toInt()).toByte() // 拗音符 (4)
    private val PREFIX_YOU_DAKUTEN = (DOT_4.toInt() or DOT_5.toInt()).toByte() // 拗濁音符 (4-5)
    private val PREFIX_YOU_HANDAKUTEN = (DOT_4.toInt() or DOT_6.toInt()).toByte() // 拗半濁音符 (4-6)

    // 基本ひらがなテーブル (母音と子音の組み合わせ 6点点字)
    // 母音: あ(1), い(1-2), う(1-4), え(1-2-4), お(2-4)
    private val HIRAGANA_MAP = mapOf(
        'あ' to byteArrayOf(DOT_1),
        'い' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt()).toByte()),
        'う' to byteArrayOf((DOT_1.toInt() or DOT_4.toInt()).toByte()),
        'え' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt()).toByte()),
        'お' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt()).toByte()),

        // か行 (母音 + 6点)
        'か' to byteArrayOf((DOT_1.toInt() or DOT_6.toInt()).toByte()),
        'き' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_6.toInt()).toByte()),
        'く' to byteArrayOf((DOT_1.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'け' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'こ' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),

        // さ行 (母音 + 5-6点)
        'さ' to byteArrayOf((DOT_1.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'し' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'す' to byteArrayOf((DOT_1.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'せ' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'そ' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),

        // た行 (母音 + 3-5点)
        'た' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_5.toInt()).toByte()),
        'ち' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_5.toInt()).toByte()),
        'つ' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'て' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'と' to byteArrayOf((DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),

        // な行 (母音 + 3点)
        'な' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt()).toByte()),
        'に' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt()).toByte()),
        'ぬ' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt()).toByte()),
        'ね' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt()).toByte()),
        'の' to byteArrayOf((DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt()).toByte()),

        // は行 (母音 + 3-6点)
        'は' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_6.toInt()).toByte()),
        'ひ' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_6.toInt()).toByte()),
        'ふ' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'へ' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'ほ' to byteArrayOf((DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),

        // ま行 (母音 + 3-5-6点)
        'ま' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'み' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'む' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'め' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'も' to byteArrayOf((DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),

        // や行 (3-4点 + 母音)
        'や' to byteArrayOf((DOT_3.toInt() or DOT_4.toInt()).toByte()),
        'ゆ' to byteArrayOf((DOT_3.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'よ' to byteArrayOf((DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),

        // ら行 (母音 + 5点)
        'ら' to byteArrayOf((DOT_1.toInt() or DOT_5.toInt()).toByte()),
        'り' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_5.toInt()).toByte()),
        'る' to byteArrayOf((DOT_1.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'れ' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'ろ' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),

        // わ行
        'わ' to byteArrayOf((DOT_3.toInt()).toByte()),
        'を' to byteArrayOf((DOT_3.toInt() or DOT_5.toInt()).toByte()),
        'ん' to byteArrayOf((DOT_3.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'っ' to byteArrayOf((DOT_2.toInt()).toByte()),
        'ー' to byteArrayOf((DOT_2.toInt() or DOT_5.toInt()).toByte())
    )

    // アルファベット (A-Z, 英語点字)
    private val ALPHABET_MAP = mapOf(
        'a' to byteArrayOf(DOT_1),
        'b' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt()).toByte()),
        'c' to byteArrayOf((DOT_1.toInt() or DOT_4.toInt()).toByte()),
        'd' to byteArrayOf((DOT_1.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'e' to byteArrayOf((DOT_1.toInt() or DOT_5.toInt()).toByte()),
        'f' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt()).toByte()),
        'g' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'h' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_5.toInt()).toByte()),
        'i' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt()).toByte()),
        'j' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'k' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt()).toByte()),
        'l' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt()).toByte()),
        'm' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt()).toByte()),
        'n' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'o' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_5.toInt()).toByte()),
        'p' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt()).toByte()),
        'q' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'r' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_5.toInt()).toByte()),
        's' to byteArrayOf((DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt()).toByte()),
        't' to byteArrayOf((DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()),
        'u' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_6.toInt()).toByte()),
        'v' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_3.toInt() or DOT_6.toInt()).toByte()),
        'w' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'x' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'y' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'z' to byteArrayOf((DOT_1.toInt() or DOT_3.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),

        // エスペラント固有文字 (ĉ, ĝ, ĥ, ĵ, ŝ, ŭ)
        'ĉ' to byteArrayOf((DOT_1.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'ĝ' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'ĥ' to byteArrayOf((DOT_1.toInt() or DOT_2.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'ĵ' to byteArrayOf((DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt() or DOT_6.toInt()).toByte()),
        'ŝ' to byteArrayOf((DOT_2.toInt() or DOT_3.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte()),
        'ŭ' to byteArrayOf((DOT_3.toInt() or DOT_4.toInt() or DOT_6.toInt()).toByte())
    )

    // 数字 (1-9, 0 は a-j と同じドット構成で数符を伴う)
    private val DIGIT_MAP = mapOf(
        '1' to DOT_1,
        '2' to (DOT_1.toInt() or DOT_2.toInt()).toByte(),
        '3' to (DOT_1.toInt() or DOT_4.toInt()).toByte(),
        '4' to (DOT_1.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte(),
        '5' to (DOT_1.toInt() or DOT_5.toInt()).toByte(),
        '6' to (DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt()).toByte(),
        '7' to (DOT_1.toInt() or DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte(),
        '8' to (DOT_1.toInt() or DOT_2.toInt() or DOT_5.toInt()).toByte(),
        '9' to (DOT_2.toInt() or DOT_4.toInt()).toByte(),
        '0' to (DOT_2.toInt() or DOT_4.toInt() or DOT_5.toInt()).toByte()
    )

    /**
     * 文字列を点字バイト配列に変換する
     */
    fun translate(text: String?): ByteArray {
        if (text.isNullOrEmpty()) return byteArrayOf()

        val output = mutableListOf<Byte>()
        var inNumberMode = false
        var inLatinMode = false

        var i = 0
        while (i < text.length) {
            val ch = text[i]

            when {
                // 空白
                ch.isWhitespace() -> {
                    output.add(DOT_BLANK)
                    inNumberMode = false
                    inLatinMode = false
                }

                // 数字 (0-9)
                ch.isDigit() -> {
                    if (!inNumberMode) {
                        output.add(PREFIX_NUMBER)
                        inNumberMode = true
                        inLatinMode = false
                    }
                    val digitDots = DIGIT_MAP[ch] ?: DOT_BLANK
                    output.add(digitDots)
                }

                // アルファベット (A-Z, a-z, エスペラント文字)
                ch in 'a'..'z' || ch in 'A'..'Z' || ALPHABET_MAP.containsKey(ch.lowercaseChar()) -> {
                    if (!inLatinMode) {
                        output.add(PREFIX_LATIN)
                        inLatinMode = true
                        inNumberMode = false
                    }
                    val lower = ch.lowercaseChar()
                    val bytes = ALPHABET_MAP[lower] ?: byteArrayOf(DOT_BLANK)
                    for (b in bytes) output.add(b)
                }

                // ひらがな / カタカナ
                else -> {
                    inNumberMode = false
                    inLatinMode = false

                    // カタカナをひらがなに正規化
                    val hira = if (ch in '\u30A1'..'\u30F6') (ch.code - 0x60).toChar() else ch

                    // 濁音・半濁音の分解チェック
                    val dakutenMap = mapOf(
                        'が' to 'か', 'ぎ' to 'き', 'ぐ' to 'く', 'げ' to 'け', 'ご' to 'こ',
                        'ざ' to 'さ', 'じ' to 'し', 'ず' to 'す', 'ぜ' to 'せ', 'ぞ' to 'そ',
                        'だ' to 'た', 'ぢ' to 'ち', 'づ' to 'つ', 'で' to 'て', 'ど' to 'と',
                        'ば' to 'は', 'び' to 'ひ', 'ぶ' to 'ふ', 'べ' to 'へ', 'ぼ' to 'ほ'
                    )
                    val handakutenMap = mapOf(
                        'ぱ' to 'は', 'ぴ' to 'ひ', 'ぷ' to 'ふ', 'ぺ' to 'へ', 'ぽ' to 'ほ'
                    )

                    if (dakutenMap.containsKey(hira)) {
                        output.add(PREFIX_DAKUTEN)
                        val base = dakutenMap[hira]!!
                        val bytes = HIRAGANA_MAP[base] ?: byteArrayOf(DOT_BLANK)
                        for (b in bytes) output.add(b)
                    } else if (handakutenMap.containsKey(hira)) {
                        output.add(PREFIX_HANDAKUTEN)
                        val base = handakutenMap[hira]!!
                        val bytes = HIRAGANA_MAP[base] ?: byteArrayOf(DOT_BLANK)
                        for (b in bytes) output.add(b)
                    } else if (HIRAGANA_MAP.containsKey(hira)) {
                        val bytes = HIRAGANA_MAP[hira]!!
                        for (b in bytes) output.add(b)
                    } else {
                        // 未知の文字（漢字等はそのまま空白＋点字フォールバック）
                        output.add(DOT_BLANK)
                    }
                }
            }
            i++
        }

        return output.toByteArray()
    }

    /**
     * 点字バイト配列を Unicode 点字文字列（例: ⠓⠑⠇⠇⠕）に変換
     */
    fun toUnicodeBrailleString(dotsArray: ByteArray): String {
        val sb = java.lang.StringBuilder()
        for (b in dotsArray) {
            sb.append(BrailleConstants.byteToUnicodeChar(b))
        }
        return sb.toString()
    }
}
