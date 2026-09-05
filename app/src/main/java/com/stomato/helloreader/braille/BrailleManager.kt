package com.stomato.helloreader.braille

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

/**
 * 点字入出力マネージャー
 * 点字ディスプレイとの接続管理、点字翻訳、および点字キー操作のディスパッチを担当
 */
class BrailleManager(private val context: Context) {

    companion object {
        private const val TAG = "BrailleManager"
    }

    private var activeDriver: BrailleDriver = BrailleSenseDriver(cellCount = 20)
    private val executor = Executors.newSingleThreadExecutor()

    val bluetoothManager: BrailleBluetoothManager by lazy {
        BrailleBluetoothManager(
            context = context,
            onDataReceived = { data -> onRawDataReceived(data) },
            onConnectionStateChanged = { connected, deviceName ->
                Log.d(TAG, "Braille connection changed: connected=$connected, device=$deviceName")
                if (connected) {
                    displayText("点字端末に接続しました")
                }
            }
        )
    }

    // 現在点字ディスプレイに表示中のテキストと点字データ
    var currentBrailleText: String = ""
        private set
    var currentUnicodeBraille: String = ""
        private set

    // 点字キー入力時のアクションリスナー
    var onBrailleActionListener: ((BrailleConstants.BrailleKey) -> Unit)? = null

    /**
     * UIノードのフォーカス更新時に点字データを生成して送信
     */
    @Suppress("DEPRECATION")
    fun displayNode(node: AccessibilityNodeInfo?, roleDescription: String? = null) {
        if (node == null) return

        executor.execute {
            try {
                val nodeText = node.text?.toString()
                val contentDesc = node.contentDescription?.toString()
                val className = node.className?.toString() ?: ""

                // コントロール種別の点字プレフィックスを付与
                val rolePrefix = when {
                    className.contains("Button") || node.isClickable -> "[ボタン] "
                    className.contains("EditText") -> "[入力] "
                    className.contains("CheckBox") -> if (node.isChecked) "[チェック済] " else "[未チェック] "
                    className.contains("Switch") -> if (node.isChecked) "[オン] " else "[オフ] "
                    !roleDescription.isNullOrEmpty() -> "[$roleDescription] "
                    else -> ""
                }

                val rawText = when {
                    !contentDesc.isNullOrEmpty() -> contentDesc
                    !nodeText.isNullOrEmpty() -> nodeText
                    else -> ""
                }

                val fullText = "$rolePrefix$rawText".trim()
                if (fullText.isEmpty()) return@execute

                currentBrailleText = fullText
                val brailleBytes = JapaneseBrailleTranslator.translate(fullText)
                currentUnicodeBraille = JapaneseBrailleTranslator.toUnicodeBrailleString(brailleBytes)

                Log.d(TAG, "Braille Output: [$fullText] -> $currentUnicodeBraille (bytes: ${brailleBytes.size})")

                // 実機パケットの生成とBluetooth送信
                val packet = activeDriver.formatDisplayPacket(brailleBytes)
                bluetoothManager.sendData(packet)

            } catch (e: Exception) {
                Log.e(TAG, "Error generating braille display", e)
            }
        }
    }

    /**
     * 任意のテキストを点字ディスプレイに表示
     */
    fun displayText(text: String) {
        if (text.isEmpty()) return
        executor.execute {
            try {
                currentBrailleText = text
                val brailleBytes = JapaneseBrailleTranslator.translate(text)
                currentUnicodeBraille = JapaneseBrailleTranslator.toUnicodeBrailleString(brailleBytes)
                Log.d(TAG, "Braille Text Output: [$text] -> $currentUnicodeBraille")

                val packet = activeDriver.formatDisplayPacket(brailleBytes)
                bluetoothManager.sendData(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying raw text on braille", e)
            }
        }
    }

    /**
     * 点字端末からの受信データを処理
     */
    fun onRawDataReceived(data: ByteArray) {
        val key = activeDriver.parseInputPacket(data)
        if (key != null) {
            Log.d(TAG, "Braille Key Detected: $key")
            onBrailleActionListener?.invoke(key)
        }
    }

    fun shutdown() {
        bluetoothManager.shutdown()
        executor.shutdown()
    }
}
