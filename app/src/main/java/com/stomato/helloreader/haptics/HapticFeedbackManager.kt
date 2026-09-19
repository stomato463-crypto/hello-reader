package com.stomato.helloreader.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 盲ろう者・視覚障害者向け 触覚（ハプティクス）フィードバックマネージャー
 * 画面を見ず、音を聞かなくても指先の振動で端末状態が把握できるようにパターンを明確化
 */
class HapticFeedbackManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * フォーカス移動時（軽快な極短パルス: 15ms）
     */
    fun performFocus() {
        vibrate(timings = longArrayOf(0, 15), amplitudes = intArrayOf(0, 120))
    }

    /**
     * ボタン決定・クリック実行時（重みのあるしっかりしたパルス: 40ms）
     */
    fun performClick() {
        vibrate(timings = longArrayOf(0, 40), amplitudes = intArrayOf(0, 255))
    }

    /**
     * スクロール端到達 / 操作不可エラー時（警告長振動: 120ms）
     */
    fun performBoundaryOrError() {
        vibrate(timings = longArrayOf(0, 120), amplitudes = intArrayOf(0, 200))
    }

    /**
     * 着信 / 重要通知受信時（リズミカルな2連パルス: トントン）
     */
    fun performNotification() {
        vibrate(timings = longArrayOf(0, 60, 80, 60), amplitudes = intArrayOf(0, 220, 0, 220))
    }

    /**
     * モールス符号（. : 短点 50ms, - : 長点 150ms）に従って振動フィードバックを実行
     */
    fun performMorsePattern(morsePattern: String) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator() || morsePattern.isBlank()) return

        val timingsList = mutableListOf<Long>()
        val amplitudesList = mutableListOf<Int>()

        timingsList.add(0L)
        amplitudesList.add(0)

        for (i in morsePattern.indices) {
            val c = morsePattern[i]
            val duration = if (c == '.') 50L else 150L
            timingsList.add(duration)
            amplitudesList.add(200)

            if (i < morsePattern.length - 1) {
                timingsList.add(50L) // 符間 50ms
                amplitudesList.add(0)
            }
        }

        vibrate(timings = timingsList.toLongArray(), amplitudes = amplitudesList.toIntArray())
    }

    /**
     * 6点点字の1バイト表現（bit 0=Dot1, bit 1=Dot2, ..., bit 5=Dot6）に従って
     * 6つのドットパルスを順次振動出力する
     */
    fun performBrailleByte(dots: Byte) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val timingsList = mutableListOf<Long>()
        val amplitudesList = mutableListOf<Int>()

        timingsList.add(0L)
        amplitudesList.add(0)

        appendBrailleBytePattern(dots, timingsList, amplitudesList)

        if (timingsList.size > 1) {
            vibrate(timings = timingsList.toLongArray(), amplitudes = amplitudesList.toIntArray())
        }
    }

    /**
     * 文字列を点字翻訳して単一の連続触覚パルス振動を出力
     */
    /**
     * 文字列を点字翻訳して「体表点字方式」（イシュー#3仕様）による点字振動を出力
     * - 短点（短い振動パルス）: 130ms
     * - 長点（長い振動パルス）: 280ms
     * - ブロック間の区切り（左列→右列の間隔）: 180ms
     * - 1文字全体の長さ: 0.8秒〜1.2秒（着信音1フレーズのリズム）
     */
    fun performBrailleText(text: String) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator() || text.isBlank()) return

        val trimmedText = text.trim()
        // 最終防衛ガード: 20文字を超える長文の過剰・暴走振動を防止
        if (trimmedText.length > 20) {
            Log.d("HapticFeedbackManager", "Braille haptic skipped for long text (length=${trimmedText.length})")
            return
        }

        val brailleBytes = com.stomato.helloreader.braille.JapaneseBrailleTranslator.translate(trimmedText)
        if (brailleBytes.isEmpty()) return

        val timingsList = mutableListOf<Long>()
        val amplitudesList = mutableListOf<Int>()

        timingsList.add(0L)
        amplitudesList.add(0)

        for (i in brailleBytes.indices) {
            appendBodySurfaceBrailleBytePattern(brailleBytes[i], timingsList, amplitudesList)
            if (i < brailleBytes.size - 1) {
                // セル間（文字間）休止: 200ms
                timingsList.add(200L)
                amplitudesList.add(0)
            }
        }

        if (timingsList.size > 1) {
            vibrate(timings = timingsList.toLongArray(), amplitudes = amplitudesList.toIntArray())
        }
    }

    private fun appendBodySurfaceBrailleBytePattern(
        dots: Byte,
        timingsList: MutableList<Long>,
        amplitudesList: MutableList<Int>
    ) {
        val intVal = dots.toInt() and 0x3F
        if (intVal == 0) {
            timingsList.add(300L)
            amplitudesList.add(0)
            return
        }

        // 左列ブロック (1, 2, 3の点)
        val leftDotsCount = Integer.bitCount(intVal and 0x07)
        when (leftDotsCount) {
            1 -> {
                timingsList.add(130L)  // 短点 130ms
                amplitudesList.add(240)
            }
            2, 3 -> {
                timingsList.add(280L)  // 長点 280ms
                amplitudesList.add(255)
            }
            else -> {
                timingsList.add(100L)  // 無音
                amplitudesList.add(0)
            }
        }

        // ブロック間の区切り (左列 → 右列) : 180ms
        timingsList.add(180L)
        amplitudesList.add(0)

        // 右列ブロック (4, 5, 6の点)
        val rightDotsCount = Integer.bitCount((intVal and 0x38) shr 3)
        when (rightDotsCount) {
            1 -> {
                timingsList.add(130L)  // 短点 130ms
                amplitudesList.add(240)
            }
            2, 3 -> {
                timingsList.add(280L)  // 長点 280ms
                amplitudesList.add(255)
            }
            else -> {
                timingsList.add(100L)  // 無音
                amplitudesList.add(0)
            }
        }
    }

    private fun appendBrailleBytePattern(
        dots: Byte,
        timingsList: MutableList<Long>,
        amplitudesList: MutableList<Int>
    ) {
        val intVal = dots.toInt() and 0x3F // 6点点字のドット(1~6)を抽出
        if (intVal == 0) {
            // 空白文字: 100ms 休止
            timingsList.add(100L)
            amplitudesList.add(0)
            return
        }

        for (dotIndex in 0 until 6) {
            val isPresent = ((intVal shr dotIndex) and 1) != 0
            if (isPresent) {
                timingsList.add(35L)   // Dot ON: 35ms クリスプパルス振動
                amplitudesList.add(255) // 最大振動強度
            } else {
                timingsList.add(25L)   // Dot OFF: 25ms 休止
                amplitudesList.add(0)
            }

            if (dotIndex < 5) {
                timingsList.add(25L)   // 点間パルス間隔 25ms
                amplitudesList.add(0)
            }
        }
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vib.vibrate(effect)
            } catch (e: Exception) {
                // フォールバック
                @Suppress("DEPRECATION")
                vib.vibrate(timings.sum())
            }
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(timings.sum())
        }
    }
}
