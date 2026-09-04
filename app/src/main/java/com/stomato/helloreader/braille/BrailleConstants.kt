package com.stomato.helloreader.braille

/**
 * 点字関連の定数定義
 * 6点点字（Dot 1〜6）および8点点字（Dot 1〜8）、Unicode点字ブロック
 */
object BrailleConstants {
    // 6点/8点点字の各ドットビットマスク
    const val DOT_1: Byte = 0x01.toByte() // 左上
    const val DOT_2: Byte = 0x02.toByte() // 左中
    const val DOT_3: Byte = 0x04.toByte() // 左下
    const val DOT_4: Byte = 0x08.toByte() // 右上
    const val DOT_5: Byte = 0x10.toByte() // 右中
    const val DOT_6: Byte = 0x20.toByte() // 右下
    const val DOT_7: Byte = 0x40.toByte() // 左最下 (8点点字)
    const val DOT_8: Byte = 0x80.toByte() // 右最下 (8点点字)

    const val DOT_BLANK: Byte = 0x00.toByte()

    // Unicode 点字パターンのベース (U+2800)
    const val UNICODE_BRAILLE_BASE = 0x2800

    /**
     * 点字バイト (bit 0=Dot1, bit 1=Dot2, ..., bit 7=Dot8) を Unicode点字文字に変換
     */
    fun byteToUnicodeChar(dots: Byte): Char {
        val intVal = dots.toInt() and 0xFF
        return (UNICODE_BRAILLE_BASE + intVal).toChar()
    }

    /**
     * Unicode点字文字 (U+2800〜U+28FF) を点字バイトに変換
     */
    fun unicodeCharToByte(c: Char): Byte {
        val code = c.code
        return if (code in UNICODE_BRAILLE_BASE..(UNICODE_BRAILLE_BASE + 0xFF)) {
            (code - UNICODE_BRAILLE_BASE).toByte()
        } else {
            DOT_BLANK
        }
    }

    // 点字ディスプレイからの汎用キーコード定義
    enum class BrailleKey {
        NAVIGATE_NEXT,        // 次の項目へ（パン右 / スクロール進む）
        NAVIGATE_PREVIOUS,    // 前の項目へ（パン左 / スクロール戻る）
        ACTIVATE,             // 決定 / クリック (Cursor Routing / Space + Dot8 / Enter)
        SCROLL_UP,            // 上スクロール
        SCROLL_DOWN,          // 下スクロール
        GLOBAL_HOME,          // ホーム画面へ
        GLOBAL_BACK,          // 戻る
        GLOBAL_RECENTS,       // 最近使ったアプリ
        GLOBAL_NOTIFICATIONS, // 通知を開く
        GLOBAL_QUICK_SETTINGS,// クイック設定を開く
        READ_TIME,            // 現在時刻読み上げ＆点字表示 (Space + T)
        READ_BATTERY,         // バッテリー残量・状態読み上げ (Space + B)
        READ_FROM_TOP,        // 画面上部からの全連続音読 (Space + R)
        TOGGLE_SPEECH         // 音声の一時停止・消音 (Space + S)
    }
}
