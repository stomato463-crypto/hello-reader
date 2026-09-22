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

        // 1. Gboard 複合キーノード (例: "a ALPHA", "b BRAVO", "s SIERRA") の分離解読
        // 単一文字の場合は null ヒントで返し、HelloReaderService.resolveCharPhonetics で
        // 言語モード（日本語かな / 英語アルファベット）に応じた高精度フォネティック解決を行う
        val parts = text.split("\\s+".toRegex())
        if (parts.size >= 2 && parts[0].length == 1) {
            val singleChar = parts[0][0]
            return Pair(singleChar.toString(), null)
        }

        // 2. Gboard 単一文字キー ('a', 'b', 's' 等)
        if (text.length == 1) {
            val singleChar = text[0]
            return Pair(singleChar.toString(), null)
        }

        // 3. Gboard モード切替・地球儀キー・特殊キーの救済
        if (text.contains("言語") || text.contains("キーボード") || text.contains("入力方法")) {
            return Pair(text, "ダブルタップで入力言語を切り替えます")
        }

        return null
    }
}

