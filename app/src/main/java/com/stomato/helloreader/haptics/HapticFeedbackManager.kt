package com.stomato.helloreader.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

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
     * 6つのドットパルスを順次振動出力する（15ms 振動, 30ms 休止）
     */
    fun performBrailleByte(dots: Byte) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val timingsList = mutableListOf<Long>()
        val amplitudesList = mutableListOf<Int>()

        timingsList.add(0L)
        amplitudesList.add(0)

        val intVal = dots.toInt() and 0x3F // 6点点字のドット(1~6)を抽出
        if (intVal == 0) {
            // 空白文字: 休止
            vibrate(timings = longArrayOf(0, 0, 60), amplitudes = intArrayOf(0, 0, 0))
            return
        }

        for (dotIndex in 0 until 6) {
            val isPresent = ((intVal shr dotIndex) and 1) != 0
            if (isPresent) {
                timingsList.add(15L)  // Dot ON: 15ms 振動
                amplitudesList.add(220)
            } else {
                timingsList.add(0L)   // Dot OFF: 振動なし
                amplitudesList.add(0)
            }

            if (dotIndex < 5) {
                timingsList.add(30L)  // 点間パルス間隔 30ms
                amplitudesList.add(0)
            }
        }

        vibrate(timings = timingsList.toLongArray(), amplitudes = amplitudesList.toIntArray())
    }

    /**
     * 文字列を点字翻訳して触覚パルス振動を出力
     */
    fun performBrailleText(text: String) {
        if (text.isBlank()) return
        val brailleBytes = com.stomato.helloreader.braille.JapaneseBrailleTranslator.translate(text)
        for (b in brailleBytes) {
            performBrailleByte(b)
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
