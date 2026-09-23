package com.stomato.helloreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日本語数字通話表 (無線局運用規則 別表第5号 通話表) および
 * 英語数字フォネティック辞書のユニットテスト
 *
 * なでしこ3公式仕様書:
 * - Japanese_Phonetic_Braille_Feedback.nako3
 * - Gboard_Touch_Exploration_Braille.nako3
 * に完全準拠。
 */
class JapanesePhoneticTableTest {

    @Test
    fun testJapaneseHalfWidthDigits_WirelessRegulationsStandard() {
        val expected = mapOf(
            '0' to "数字のまる",
            '1' to "数字のひと",
            '2' to "数字のふた",
            '3' to "数字のさん",
            '4' to "数字のよん",
            '5' to "数字のご",
            '6' to "数字のろく",
            '7' to "数字のなな",
            '8' to "数字のはち",
            '9' to "数字のきゅう"
        )

        for ((digit, expectedPhonetic) in expected) {
            val actual = JapanesePhoneticTable.getJapaneseDigitPhonetic(digit)
            assertNotNull("Digit '$digit' must have a phonetic reading", actual)
            assertEquals("Digit '$digit' phonetic reading must match Wireless Regulations standard", expectedPhonetic, actual)
            assertEquals(
                "resolveDigitPhonetic in Japanese mode must return Wireless Regulations reading",
                expectedPhonetic,
                JapanesePhoneticTable.resolveDigitPhonetic(digit, isEnglishKeyboard = false)
            )
        }
    }

    @Test
    fun testJapaneseFullWidthDigits_WirelessRegulationsStandard() {
        val fullWidthExpected = mapOf(
            '０' to "数字のまる",
            '１' to "数字のひと",
            '２' to "数字のふた",
            '３' to "数字のさん",
            '４' to "数字のよん",
            '５' to "数字のご",
            '６' to "数字のろく",
            '７' to "数字のなな",
            '８' to "数字のはち",
            '９' to "数字のきゅう"
        )

        for ((digit, expectedPhonetic) in fullWidthExpected) {
            val actual = JapanesePhoneticTable.getJapaneseDigitPhonetic(digit)
            assertNotNull("Full-width digit '$digit' must have a phonetic reading", actual)
            assertEquals("Full-width digit '$digit' must match Wireless Regulations reading", expectedPhonetic, actual)
            assertEquals(
                "resolveDigitPhonetic in Japanese mode for full-width '$digit' must return Wireless Regulations reading",
                expectedPhonetic,
                JapanesePhoneticTable.resolveDigitPhonetic(digit, isEnglishKeyboard = false)
            )
        }
    }

    @Test
    fun testJapaneseKanjiNumerals() {
        val kanjiExpected = mapOf(
            '〇' to "数字のまる",
            '一' to "数字のひと",
            '二' to "数字のふた",
            '三' to "数字のさん",
            '四' to "数字のよん",
            '五' to "数字のご",
            '六' to "数字のろく",
            '七' to "数字のなな",
            '八' to "数字のはち",
            '九' to "数字のきゅう"
        )

        for ((kanji, expectedPhonetic) in kanjiExpected) {
            val actual = JapanesePhoneticTable.getJapaneseDigitPhonetic(kanji)
            assertNotNull("Kanji numeral '$kanji' must have a phonetic reading", actual)
            assertEquals("Kanji numeral '$kanji' must match Wireless Regulations reading", expectedPhonetic, actual)
        }
    }

    @Test
    fun testEnglishDigits_HalfWidthAndFullWidth() {
        val expectedEnglish = mapOf(
            '0' to "ゼロ",
            '1' to "ワン",
            '2' to "ツー",
            '3' to "スリー",
            '4' to "フォー",
            '5' to "ファイブ",
            '6' to "シックス",
            '7' to "セブン",
            '8' to "エイト",
            '9' to "ナイン",
            '０' to "ゼロ",
            '１' to "ワン",
            '２' to "ツー",
            '３' to "スリー",
            '４' to "フォー",
            '５' to "ファイブ",
            '６' to "シックス",
            '７' to "セブン",
            '８' to "エイト",
            '９' to "ナイン"
        )

        for ((digit, expectedPhonetic) in expectedEnglish) {
            val actual = JapanesePhoneticTable.getEnglishDigitPhonetic(digit)
            assertNotNull("English digit '$digit' must have a phonetic reading", actual)
            assertEquals("English digit '$digit' must match English phonetic reading", expectedPhonetic, actual)
            assertEquals(
                "resolveDigitPhonetic in English mode for '$digit' must return English reading",
                expectedPhonetic,
                JapanesePhoneticTable.resolveDigitPhonetic(digit, isEnglishKeyboard = true)
            )
        }
    }

    @Test
    fun testIsDigitRecognition() {
        // Half-width digits
        for (c in '0'..'9') {
            assertTrue("Expected '$c' to be recognized as digit", JapanesePhoneticTable.isDigit(c))
        }
        // Full-width digits
        for (c in '０'..'９') {
            assertTrue("Expected full-width '$c' to be recognized as digit", JapanesePhoneticTable.isDigit(c))
        }
        // Kanji numerals
        val kanjiDigits = charArrayOf('〇', '一', '二', '三', '四', '五', '六', '七', '八', '九')
        for (c in kanjiDigits) {
            assertTrue("Expected kanji '$c' to be recognized as digit", JapanesePhoneticTable.isDigit(c))
        }

        // Non-digits
        val nonDigits = charArrayOf('a', 'z', 'A', 'Z', 'あ', 'ア', '語', ' ', '\n', '@', '#', '!')
        for (c in nonDigits) {
            assertFalse("Expected non-digit '$c' to not be recognized as digit", JapanesePhoneticTable.isDigit(c))
        }
    }

    @Test
    fun testNonDigitReturnsNull() {
        assertNull(JapanesePhoneticTable.getJapaneseDigitPhonetic('a'))
        assertNull(JapanesePhoneticTable.getJapaneseDigitPhonetic('あ'))
        assertNull(JapanesePhoneticTable.getEnglishDigitPhonetic('a'))
        assertNull(JapanesePhoneticTable.resolveDigitPhonetic('z', isEnglishKeyboard = false))
        assertNull(JapanesePhoneticTable.resolveDigitPhonetic('z', isEnglishKeyboard = true))
    }

    @Test
    fun testSpecificDigitRequirements() {
        // Specifically verify "1" -> "数字のひと" (not "ワン" in Japanese mode)
        assertEquals("数字のひと", JapanesePhoneticTable.resolveDigitPhonetic('1', isEnglishKeyboard = false))
        assertEquals("ワン", JapanesePhoneticTable.resolveDigitPhonetic('1', isEnglishKeyboard = true))

        // Specifically verify "2" -> "数字のふた" (Japanese Wireless Regulations standard, not "数字のに")
        assertEquals("数字のふた", JapanesePhoneticTable.resolveDigitPhonetic('2', isEnglishKeyboard = false))
        assertEquals("ツー", JapanesePhoneticTable.resolveDigitPhonetic('2', isEnglishKeyboard = true))

        // Specifically verify "9" -> "数字のきゅう" (Wireless Regulations standard, not "きゆう")
        assertEquals("数字のきゅう", JapanesePhoneticTable.resolveDigitPhonetic('9', isEnglishKeyboard = false))
        assertEquals("ナイン", JapanesePhoneticTable.resolveDigitPhonetic('9', isEnglishKeyboard = true))
    }
}
