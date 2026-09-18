package com.stomato.helloreader.menu

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
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
 * AOSP Accessibility Overlay 直付けウィンドウ方式
 * 仕様書: src/HelloReader_Menu.nako3 準拠
 */
class HelloReaderMenuManager(private val service: HelloReaderService) {

    private var isMenuShowing: Boolean = false
    private var overlayView: View? = null
    private val windowManager: WindowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    companion object {
        const val GESTURE_SWIPE_UP_AND_RIGHT = 16
        const val GESTURE_SWIPE_DOWN_AND_RIGHT = 21
        const val GESTURE_SWIPE_UP_AND_LEFT = 17
    }

    fun isShowing(): Boolean = isMenuShowing

    /**
     * ジェスチャーIDでメニュー起動判定処理
     */
    fun onGesturePerformed(gestureId: Int): Boolean {
        // 直角ジェスチャー（16, 17, 21）の判定
        if (gestureId != GESTURE_SWIPE_UP_AND_RIGHT &&
            gestureId != GESTURE_SWIPE_DOWN_AND_RIGHT &&
            gestureId != GESTURE_SWIPE_UP_AND_LEFT &&
            gestureId != AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT &&
            gestureId != AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT
        ) {
            return false
        }

        if (isMenuShowing) {
            return false
        }

        service.interruptSpeech()
        service.playFocusSound()
        service.speakText("Hello Reader メニュー")
        showMenuOverlay()
        return true
    }

    /**
     * メニュー項目一覧定義
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

        val filteredList = if (isLocked) {
            list.filter { it.allowLocked }
        } else {
            list
        }

        return filteredList.sortedBy { it.order }
    }

    /**
     * WindowManager直付け方式でのメニュー表示処理 (AOSP TalkBack overlay 方式)
     */
    private fun showMenuOverlay() {
        dismissMenu()

        val keyguardManager = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        val hasCustomActions = service.hasCustomActionsOnCurrentFocus()
        val isScreenCurtainOn = service.isScreenCurtainActive()

        val displayItems = getMenuItemList(isLocked, hasCustomActions, isScreenCurtainOn)
        val titles = displayItems.map { it.title }

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1E1E1E"))
                cornerRadius = 24f
                setStroke(3, Color.parseColor("#44FFFFFF"))
            }
        }

        val titleView = TextView(service).apply {
            text = "Hello Reader メニュー"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 24)
            gravity = Gravity.CENTER
        }
        container.addView(titleView)

        val listView = ListView(service).apply {
            adapter = ArrayAdapter(
                service,
                android.R.layout.simple_list_item_1,
                titles
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        container.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = displayItems.getOrNull(position)
            if (item != null) {
                executeMenuAction(item.id)
            } else {
                dismissMenu()
            }
        }

        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismissMenu()
                true
            } else {
                false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        try {
            windowManager.addView(container, params)
            overlayView = container
            isMenuShowing = true

            container.postDelayed({
                try {
                    container.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                    titleView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                } catch (e: Exception) { }
            }, 100)
        } catch (e: Exception) {
            isMenuShowing = false
        }
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
        if (isMenuShowing && overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) { }
            overlayView = null
            isMenuShowing = false
        }
    }
}
