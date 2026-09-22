package com.stomato.helloreader.appsupport.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GboardProfile および Gboard言語動的判定処理のユニットテスト
 * (なでしこ3仕様書: Gboard_Touch_Exploration_Braille.nako3
 *  および Japanese_Phonetic_Braille_Feedback.nako3 準拠)
 */
class GboardProfileTest {

    @Test
    fun testCompoundKeyPreservesPhoneticToken() {
        // Gboard英語複合キー: "a ALPHA" -> ("a", "ALPHA")
        val pairA = GboardProfile.parseKeyText("a ALPHA")
        assertNotNull(pairA)
        assertEquals("a", pairA?.first)
        assertEquals("ALPHA", pairA?.second)

        // Gboard英語複合キー: "b BRAVO" -> ("b", "BRAVO")
        val pairB = GboardProfile.parseKeyText("b BRAVO")
        assertNotNull(pairB)
        assertEquals("b", pairB?.first)
        assertEquals("BRAVO", pairB?.second)

        // Gboard複合キー (日本語カナフォネティック): "a アルファ" -> ("a", "アルファ")
        val pairAlpha = GboardProfile.parseKeyText("a アルファ")
        assertNotNull(pairAlpha)
        assertEquals("a", pairAlpha?.first)
        assertEquals("アルファ", pairAlpha?.second)

        // Gboard日本語複合キー: "あ あさひのあ" -> ("あ", "あさひのあ")
        val pairAsahi = GboardProfile.parseKeyText("あ あさひのあ")
        assertNotNull(pairAsahi)
        assertEquals("あ", pairAsahi?.first)
        assertEquals("あさひのあ", pairAsahi?.second)
    }

    @Test
    fun testSingleCharacterKeys() {
        // 単一文字キー: "a" -> ("a", null)
        val singleA = GboardProfile.parseKeyText("a")
        assertNotNull(singleA)
        assertEquals("a", singleA?.first)
        assertNull(singleA?.second)

        // 単一文字キー (デフォルトヒント付き): "a", "hint" -> ("a", "hint")
        val singleWithHint = GboardProfile.parseKeyText("a", "custom_hint")
        assertNotNull(singleWithHint)
        assertEquals("a", singleWithHint?.first)
        assertEquals("custom_hint", singleWithHint?.second)

        // 単一文字かな: "あ" -> ("あ", null)
        val singleKana = GboardProfile.parseKeyText("あ")
        assertNotNull(singleKana)
        assertEquals("あ", singleKana?.first)
        assertNull(singleKana?.second)
    }

    @Test
    fun testDetectLanguageFromSpaceKey_English() {
        assertEquals("en", GboardProfile.detectLanguageFromSpaceKey("English"))
        assertEquals("en", GboardProfile.detectLanguageFromSpaceKey("English (US)"))
        assertEquals("en", GboardProfile.detectLanguageFromSpaceKey("英語"))
        assertEquals("en", GboardProfile.detectLanguageFromSpaceKey("Space"))
        assertEquals("en", GboardProfile.detectLanguageFromSpaceKey("space"))
        assertEquals("en", GboardProfile.detectLanguageFromSpaceKey("QWERTY"))
        assertEquals("en", GboardProfile.detectLanguageFromSpaceKey("Alphabet"))
    }

    @Test
    fun testDetectLanguageFromSpaceKey_Japanese() {
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("日本語"))
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("Japanese"))
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("空白"))
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("かな"))
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("フリック"))
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("ローマ字"))
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("12キー"))
        assertEquals("ja", GboardProfile.detectLanguageFromSpaceKey("テンキー"))
    }

    @Test
    fun testDetectLanguageFromSpaceKey_UnknownOrBlank() {
        assertNull(GboardProfile.detectLanguageFromSpaceKey(null))
        assertNull(GboardProfile.detectLanguageFromSpaceKey(""))
        assertNull(GboardProfile.detectLanguageFromSpaceKey("   "))
        assertNull(GboardProfile.detectLanguageFromSpaceKey("UnknownLanguage"))
    }

    @Test
    fun testIsSpaceKey() {
        assertTrue(GboardProfile.isSpaceKey(null, "Space"))
        assertTrue(GboardProfile.isSpaceKey(null, "space"))
        assertTrue(GboardProfile.isSpaceKey(null, "スペース"))
        assertTrue(GboardProfile.isSpaceKey(null, "空白"))
        assertTrue(GboardProfile.isSpaceKey(null, "English"))
    }
}
