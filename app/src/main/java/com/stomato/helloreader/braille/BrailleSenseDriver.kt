package com.stomato.helloreader.braille

/**
 * HIMS BrailleSense シリーズ (BrailleSense 6 Mini / Polaris / U2 等) 向けドライバ
 * 20セル / 32セルの点字セル出力およびパンキー・スクロールキー・エンターキー入力をサポート
 */
class BrailleSenseDriver(override val cellCount: Int = 20) : BrailleDriver {

    override val driverName: String = "HIMS BrailleSense"

    companion object {
        // HIMS パケットヘッダ
        private const val PKT_HEADER: Byte = 0x1B.toByte() // ESC
        private const val PKT_CMD_DISPLAY: Byte = 0x01.toByte()

        // HIMS 入力キーマスク
        private const val KEY_SCROLL_RIGHT: Byte = 0x20.toByte() // 次へ (パン右)
        private const val KEY_SCROLL_LEFT: Byte = 0x10.toByte()  // 前へ (パン左)
        private const val KEY_ENTER: Byte = 0x01.toByte()        // 決定 / Enter
        private const val KEY_SPACE: Byte = 0x02.toByte()
        private const val KEY_HOME: Byte = 0x04.toByte()
        private const val KEY_BACK: Byte = 0x08.toByte()
    }

    override fun formatDisplayPacket(brailleData: ByteArray, cursorPosition: Int): ByteArray {
        val payload = ByteArray(cellCount)
        val copyLen = minOf(brailleData.size, cellCount)
        System.arraycopy(brailleData, 0, payload, 0, copyLen)

        // カーソル位置に 7-8点 (0xC0) を付加
        if (cursorPosition in 0 until cellCount) {
            payload[cursorPosition] = (payload[cursorPosition].toInt() or 0xC0).toByte()
        }

        val packet = ByteArray(4 + cellCount)
        packet[0] = PKT_HEADER
        packet[1] = PKT_CMD_DISPLAY
        packet[2] = cellCount.toByte()
        System.arraycopy(payload, 0, packet, 3, cellCount)

        // 簡易チェックサム (XOR)
        var checksum: Byte = 0
        for (i in 0 until (3 + cellCount)) {
            checksum = (checksum.toInt() xor packet[i].toInt()).toByte()
        }
        packet[packet.size - 1] = checksum

        return packet
    }

    override fun parseInputPacket(rawData: ByteArray): BrailleConstants.BrailleKey? {
        if (rawData.isEmpty()) return null

        val firstByte = rawData[0]
        val secondByte = if (rawData.size > 1) rawData[1] else 0

        // 1. Space キー組み合わせショートカット
        if ((firstByte.toInt() and KEY_SPACE.toInt()) != 0 && rawData.size > 1) {
            val dots = secondByte.toInt() and 0xFF
            return when (dots) {
                0x1E -> BrailleConstants.BrailleKey.READ_TIME       // Space + T (Dot 2,3,4,5 = 0x1E)
                0x03 -> BrailleConstants.BrailleKey.READ_BATTERY    // Space + B (Dot 1,2 = 0x03)
                0x17 -> BrailleConstants.BrailleKey.READ_FROM_TOP   // Space + R (Dot 1,2,3,5 = 0x17)
                0x0E -> BrailleConstants.BrailleKey.TOGGLE_SPEECH   // Space + S (Dot 2,3,4 = 0x0E)
                0x1D -> BrailleConstants.BrailleKey.GLOBAL_NOTIFICATIONS // Space + N (Dot 1,3,4,5 = 0x1D)
                0x1F -> BrailleConstants.BrailleKey.GLOBAL_QUICK_SETTINGS // Space + Q (Dot 1,2,3,4,5 = 0x1F)
                0x13 -> BrailleConstants.BrailleKey.GLOBAL_HOME     // Space + H (Dot 1,2,5 = 0x13)
                0x12 -> BrailleConstants.BrailleKey.GLOBAL_BACK     // Space + E / Back (Dot 1,5 = 0x12)
                else -> null
            }
        }

        // 2. 単体キー操作
        return when {
            // スクロール右 (次へ)
            (firstByte.toInt() and KEY_SCROLL_RIGHT.toInt()) != 0 -> BrailleConstants.BrailleKey.NAVIGATE_NEXT
            // スクロール左 (前へ)
            (firstByte.toInt() and KEY_SCROLL_LEFT.toInt()) != 0 -> BrailleConstants.BrailleKey.NAVIGATE_PREVIOUS
            // エンター (決定)
            (firstByte.toInt() and KEY_ENTER.toInt()) != 0 -> BrailleConstants.BrailleKey.ACTIVATE
            // 戻る
            (firstByte.toInt() and KEY_BACK.toInt()) != 0 -> BrailleConstants.BrailleKey.GLOBAL_BACK
            // ホーム
            (firstByte.toInt() and KEY_HOME.toInt()) != 0 -> BrailleConstants.BrailleKey.GLOBAL_HOME
            else -> null
        }
    }
}
