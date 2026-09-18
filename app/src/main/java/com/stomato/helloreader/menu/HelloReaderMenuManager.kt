package com.stomato.helloreader.menu

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import com.stomato.helloreader.HelloReaderService
import com.stomato.helloreader.MainActivity

/**
 * メニュー項目を保持するデータクラス
 */
data class MenuItem(
    val id: String,
    val order: Int,
    val title: String,
    val action: String,
    val allowLocked: Boolean
)

/**
 * Hello Reader グローバルコンテキストメニューマネージャー
 * 仕様書: src/HelloReader_Menu.nako3 準拠
 */
class HelloReaderMenuManager(private val service: HelloReaderService) {

    private var isMenuShowing: Boolean = false
    private var activeDialog: AlertDialog? = null

    companion object {
        // TalkBack標準直角ジェスチャーID
        const val GESTURE_SWIPE_DOWN_AND_RIGHT = 17
        const val GESTURE_SWIPE_UP_AND_RIGHT = 21
    }

    /**
     * ジェスチャーIDでメニュー起動判定処理
     */
    fun onGesturePerformed(gestureId: Int): Boolean {
        // 【ガード1】直角ジェスチャー（下→右 または 上→右）以外は無視して終了
        if (gestureId != GESTURE_SWIPE_DOWN_AND_RIGHT && gestureId != GESTURE_SWIPE_UP_AND_RIGHT) {
            return false
        }

        // 【ガード2】現在すでにメニューが表示されている場合は重複表示を防止
        if (isMenuShowing) {
            return false
        }

        // 音声出力を即時中断してフォーカス音を再生
        service.interruptSpeech()
        service.playFocusSound()

        // メニュー構築と表示
        service.speakText("Hello Reader メニュー")
        showMenuDialog()
        return true
    }

    /**
     * メニュー項目一覧定義（TalkBack context_menu.xml 準拠 ＋ hello-reader独自機能）
     */
    fun getMenuItemList(
        isLocked: Boolean,
        hasCustomActions: Boolean,
        isScreenCurtainOn: Boolean
    ): List<MenuItem> {
        val list = mutableListOf<MenuItem>()

        // --- グループ1: 連続読み上げ ---
        list.add(MenuItem("read_from_top", 8, "最上部から読み上げる", "最上部から連続読み上げ", false))
        list.add(MenuItem("read_from_current", 9, "フォーカスした項目から読み上げ", "現在位置から連続読み上げ", false))

        // --- グループ2: 直前フレーズの操作 ---
        list.add(MenuItem("copy_last_utterance", 10, "最後に読み上げたフレーズをコピー", "直前テキストクリップボード保存", false))
        list.add(MenuItem("spell_last_utterance", 11, "最後に読み上げたフレーズのスペルを確認", "直前テキストスペル詳細読み", true))
        list.add(MenuItem("repeat_last_utterance", 12, "最後に読み上げたフレーズを繰り返す", "直前テキスト再発声", true))

        // --- グループ3: 画面操作・テキスト編集 ---
        if (hasCustomActions) {
            list.add(MenuItem("custom_actions", 2, "操作（アクション）", "カスタムアクションメニュー表示", false))
        }
        list.add(MenuItem("screen_search", 19, "画面検索", "画面内テキスト検索起動", false))

        // --- グループ4: クイック設定・フィードバック切り替え ---
        list.add(MenuItem("audio_ducking", 16, "他の音量を下げる", "オーディオダッキング切替", true))
        list.add(MenuItem("sound_feedback", 17, "音によるフィードバック", "効果音フィードバック切替", true))
        list.add(MenuItem("vibration_feedback", 18, "バイブ フィードバック", "振動フィードバック切替", true))

        // --- hello-reader独自：点字・モールス触覚フィードバックON/OFF切り替え ---
        list.add(MenuItem("braille_display_toggle", 30, "点字ディスプレイ ON/OFF", "点字ディスプレイ出力切替", true))
        list.add(MenuItem("morse_feedback_toggle", 31, "モールス信号フィードバック ON/OFF", "モールス信号フィードバック切替", true))
        list.add(MenuItem("braille_haptic_toggle", 32, "点字フィードバック ON/OFF", "6点点字パルス振動切替", true))

        if (isScreenCurtainOn) {
            list.add(MenuItem("screen_curtain", 20, "画面を表示（スクリーンカーテン解除）", "画面表示復元", true))
        } else {
            list.add(MenuItem("screen_curtain", 20, "画面を隠す（スクリーンカーテン）", "画面暗転実行", true))
        }

        // --- グループ5: 設定画面起動 ---
        list.add(MenuItem("verbosity", 13, "読み上げの詳細設定", "詳細度設定画面起動", false))
        list.add(MenuItem("tts_settings", 24, "テキスト読み上げ", "OS標準TTS設定起動", false))
        list.add(MenuItem("braille_display_settings", 25, "点字ディスプレイの設定", "点字ディスプレイ設定起動", false))
        list.add(MenuItem("helloreader_settings", 23, "Hello Reader の設定", "メイン設定画面起動", false))

        // ロック画面での情報保護ガード：ロック中は allowLocked == true の項目のみ抽出
        val filteredList = if (isLocked) {
            list.filter { it.allowLocked }
        } else {
            list
        }

        // 順序（order）で昇順ソート
        return filteredList.sortedBy { it.order }
    }

    /**
     * メニューダイアログ表示処理
     */
    private fun showMenuDialog() {
        val keyguardManager = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        val hasCustomActions = service.hasCustomActionsOnCurrentFocus()
        val isScreenCurtainOn = service.isScreenCurtainActive()

        val displayItems = getMenuItemList(isLocked, hasCustomActions, isScreenCurtainOn)
        val titles = displayItems.map { it.title }.toTypedArray()

        val themeContext = android.view.ContextThemeWrapper(
            service,
            android.R.style.Theme_DeviceDefault_Settings
        )
        val builder = AlertDialog.Builder(themeContext)
        builder.setTitle("Hello Reader メニュー")
        builder.setItems(titles) { _, which ->
            val selectedItem = displayItems.getOrNull(which)
            if (selectedItem != null) {
                executeMenuAction(selectedItem.id)
            } else {
                dismissMenu()
            }
        }
        builder.setOnCancelListener {
            dismissMenu()
        }

        val dialog = builder.create()
        dialog.window?.let { window ->
            window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes.dimAmount = 0.5f
        }

        isMenuShowing = true
        activeDialog = dialog
        dialog.show()

        // ダイアログ表示直後にアクセシビリティフォーカスをダイアログ内に移動し背景の読み上げを防止
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val decorView = dialog.window?.decorView
                decorView?.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                decorView?.performAccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null
                )
            } catch (e: Exception) { }
        }, 150)
    }

    /**
     * メニューアクション実行処理
     */
    fun executeMenuAction(itemId: String) {
        dismissMenu()

        when (itemId) {
            "read_from_top" -> {
                service.speakText("最上部から読み上げます")
                service.startContinuousReadingFromTop()
            }
            "read_from_current" -> {
                service.startContinuousReadingFromCurrent()
            }
            "copy_last_utterance" -> {
                val lastText = service.getLastUtteranceText()
                if (!lastText.isNullOrEmpty()) {
                    service.copyToClipboard(lastText)
                    service.speakText("直前に読み上げたフレーズをクリップボードにコピーしました")
                } else {
                    service.speakText("コピーできる読み上げフレーズがありません")
                }
            }
            "spell_last_utterance" -> {
                val lastText = service.getLastUtteranceText()
                if (!lastText.isNullOrEmpty()) {
                    service.spellPhoneticText(lastText)
                } else {
                    service.speakText("確認できる読み上げフレーズがありません")
                }
            }
            "repeat_last_utterance" -> {
                val lastText = service.getLastUtteranceText()
                if (!lastText.isNullOrEmpty()) {
                    service.speakText(lastText)
                } else {
                    service.speakText("リピートできる読み上げフレーズがありません")
                }
            }
            "custom_actions" -> {
                service.showCustomActionsDialog()
            }
            "screen_search" -> {
                service.showScreenSearchDialog()
            }
            "audio_ducking" -> {
                val newState = service.toggleAudioDuckingSetting()
                if (newState) {
                    service.speakText("他の音量を下げる設定をオンにしました")
                } else {
                    service.speakText("他の音量を下げる設定をオフにしました")
                }
            }
            "sound_feedback" -> {
                val newState = service.toggleSoundFeedbackSetting()
                if (newState) {
                    service.speakText("効果音フィードバックをオンにしました")
                } else {
                    service.speakText("効果音フィードバックをオフにしました")
                }
            }
            "vibration_feedback" -> {
                val newState = service.toggleVibrationFeedbackSetting()
                if (newState) {
                    service.speakText("バイブレーションをオンにしました")
                } else {
                    service.speakText("バイブレーションをオフにしました")
                }
            }
            "braille_display_toggle" -> {
                val newState = service.toggleBrailleDisplaySetting()
                if (newState) {
                    service.speakText("点字ディスプレイ出力をオンにしました")
                } else {
                    service.speakText("点字ディスプレイ出力をオフにしました")
                }
            }
            "morse_feedback_toggle" -> {
                val newState = service.toggleMorseFeedbackSetting()
                if (newState) {
                    service.speakText("モールス信号フィードバックをオンにしました")
                } else {
                    service.speakText("モールス信号フィードバックをオフにしました")
                }
            }
            "braille_haptic_toggle" -> {
                val newState = service.toggleBrailleHapticSetting()
                if (newState) {
                    service.speakText("点字触覚フィードバックをオンにしました")
                } else {
                    service.speakText("点字触覚フィードバックをオフにしました")
                }
            }
            "screen_curtain" -> {
                val isCurtainOn = service.isScreenCurtainActive()
                if (isCurtainOn) {
                    service.disableScreenCurtain()
                    service.speakText("画面を表示しました")
                } else {
                    service.enableScreenCurtain()
                    service.speakText("画面を暗くしました。再度このメニューから解除できます")
                }
            }
            "verbosity" -> {
                service.speakText("読み上げ詳細設定を開きます")
                service.openVerbositySettings()
            }
            "tts_settings" -> {
                service.speakText("テキスト読み上げ設定を開きます")
                try {
                    val intent = Intent("com.android.settings.TTS_SETTINGS")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(intent)
                }
            }
            "braille_display_settings" -> {
                service.speakText("点字ディスプレイ設定を開きます")
                service.openBrailleDisplaySettings()
            }
            "helloreader_settings" -> {
                service.speakText("Hello Reader の設定を開きます")
                val intent = Intent(service, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
            }
        }
    }

    /**
     * メニューを閉じる処理
     */
    fun dismissMenu() {
        if (isMenuShowing) {
            activeDialog?.dismiss()
            activeDialog = null
            isMenuShowing = false
        }
    }
}
