package com.stomato.helloreader.braille

/**
 * 点字ディスプレイ端末用抽象ドライバインターフェース
 */
interface BrailleDriver {
    /**
     * ドライバ名（例: "HIMS BrailleSense", "Generic HID"）
     */
    val driverName: String

    /**
     * 点字セル数（例: 20セル, 32セル, 40セル）
     */
    val cellCount: Int

    /**
     * 端末へ点字セルデータを書き込むパケットを生成
     * @param brailleData 点字バイト配列
     * @param cursorPosition カーソル位置 (-1 で非表示)
     * @return 端末へ送信するバイナリパケット
     */
    fun formatDisplayPacket(brailleData: ByteArray, cursorPosition: Int = -1): ByteArray

    /**
     * 端末から受信した生バイナリデータを解析し、点字キーイベントに変換
     * @param rawData 受信バイト配列
     * @return 検出されたBrailleKey (該当なしの場合はnull)
     */
    fun parseInputPacket(rawData: ByteArray): BrailleConstants.BrailleKey?
}
