package com.stomato.helloreader.braille

import android.util.Log

/**
 * 日本KGS社製「ブレールメモ」シリーズ (Braille Memo Smart BMS16 / BMS40 / BMX 等) 向けドライバ
 * KGS標準点字シリアルプロトコルに準拠し、点字出力および各種操作キーを完全サポート
 */
class BrailleMemoDriver(override val cellCount: Int = 16) : BrailleDriver {

    override val driverName: String = "KGS Braille Memo"

    companion object {
        private const val TAG = "BrailleMemoDriver"

        // KGS プロトコルヘッダ
        private const val PKT_ESC: Byte = 0x1B.toByte()
        private const val CMD_DISPLAY: Byte = 'B'.code.toByte()      // 0x42
        private const val CMD_KEY_INPUT: Byte = 'k'.code.toByte()    // 0x6B

        // KGS BMS 代表的キーマスク・コード
        private const val KEY_PAN_NEXT: Byte = 0x20.toByte()         // 進む (右送りキー)
        private const val KEY_PAN_PREV: Byte = 0x10.toByte()         // 戻る (左送りキー)
        private const val KEY_SELECT: Byte = 0x01.toByte()           // 選択 / 決定 (Enter)
        private const val KEY_SPACE: Byte = 0x02.toByte()            // スペースキー
        private const val KEY_BACK: Byte = 0x08.toByte()             // 戻る (Back)
        private const val KEY_HOME: Byte = 0x04.toByte()             // ホーム (Home)
    }

    override fun formatDisplayPacket(brailleData: ByteArray, cursorPosition: Int): ByteArray {
        val payload = ByteArray(cellCount)
        val copyLen = minOf(brailleData.size, cellCount)
        System.arraycopy(brailleData, 0, payload, 0, copyLen)

        // カーソル位置に 7-8点 (0xC0) を付加
        if (cursorPosition in 0 until cellCount) {
            payload[cursorPosition] = (payload[cursorPosition].toInt() or 0xC0).toByte()
        }

        // KGS フォーマット: [ESC, 'B', cellCount, <braille dots...>]
        val packet = ByteArray(3 + cellCount)
        packet[0] = PKT_ESC
        packet[1] = CMD_DISPLAY
        packet[2] = cellCount.toByte()
        System.arraycopy(payload, 0, packet, 3, cellCount)

        return packet
    }

    override fun parseInputPacket(rawData: ByteArray): BrailleConstants.BrailleKey? {
        if (rawData.isEmpty()) return null

        // KGS パケット解析
        // パケット先頭が ESC 'k' の場合はヘッダをスキップ
        var offset = 0
        if (rawData.size >= 2 && rawData[0] == PKT_ESC && rawData[1] == CMD_KEY_INPUT) {
            offset = 2
        }

        if (offset >= rawData.size) return null

        val firstByte = rawData[offset]
        val secondByte = if (offset + 1 < rawData.size) rawData[offset + 1] else 0

        // 1. Space キー組み合わせショートカット
        if ((firstByte.toInt() and KEY_SPACE.toInt()) != 0 && (offset + 1 < rawData.size)) {
            val dots = secondByte.toInt() and 0xFF
            return when (dots) {
                0x1E -> BrailleConstants.BrailleKey.READ_TIME       // Space + T (Dot 2,3,4,5)
                0x03 -> BrailleConstants.BrailleKey.READ_BATTERY    // Space + B (Dot 1,2)
                0x17 -> BrailleConstants.BrailleKey.READ_FROM_TOP   // Space + R (Dot 1,2,3,5)
                0x0E -> BrailleConstants.BrailleKey.TOGGLE_SPEECH   // Space + S (Dot 2,3,4)
                0x1D -> BrailleConstants.BrailleKey.GLOBAL_NOTIFICATIONS // Space + N (Dot 1,3,4,5)
                0x1F -> BrailleConstants.BrailleKey.GLOBAL_QUICK_SETTINGS // Space + Q (Dot 1,2,3,4,5)
                0x13 -> BrailleConstants.BrailleKey.GLOBAL_HOME     // Space + H (Dot 1,2,5)
                0x12 -> BrailleConstants.BrailleKey.GLOBAL_BACK     // Space + E / Back (Dot 1,5)
                else -> null
            }
        }

        // 2. 単体ナビゲーションキー操作
        return when {
            // 右送りキー (次へ)
            (firstByte.toInt() and KEY_PAN_NEXT.toInt()) != 0 -> BrailleConstants.BrailleKey.NAVIGATE_NEXT
            // 左送りキー (前へ)
            (firstByte.toInt() and KEY_PAN_PREV.toInt()) != 0 -> BrailleConstants.BrailleKey.NAVIGATE_PREVIOUS
            // 決定 / センターキー
            (firstByte.toInt() and KEY_SELECT.toInt()) != 0 -> BrailleConstants.BrailleKey.ACTIVATE
            // 戻る
            (firstByte.toInt() and KEY_BACK.toInt()) != 0 -> BrailleConstants.BrailleKey.GLOBAL_BACK
            // ホーム
            (firstByte.toInt() and KEY_HOME.toInt()) != 0 -> BrailleConstants.BrailleKey.GLOBAL_HOME
            else -> null
        }
    }
}
