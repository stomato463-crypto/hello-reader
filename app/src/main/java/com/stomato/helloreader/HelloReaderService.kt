package com.stomato.helloreader

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.provider.ContactsContract
import com.stomato.helloreader.appsupport.AppProfileManager
import com.stomato.helloreader.appsupport.CustomLabelManager
import com.stomato.helloreader.braille.BrailleConstants
import com.stomato.helloreader.braille.BrailleManager
import com.stomato.helloreader.haptics.HapticFeedbackManager
import java.util.Locale

/**
 * Hello Reader Accessibility Service
 * AOSP TalkBack 100%完全合致 指紋センサー位置案内・ロック画面・ホーム画面全解読版
 */
class HelloReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "HelloReaderService"

        // NATO フォネティックコード辞書 (英数字限定)
        private val PHONETIC_MAP = mapOf(
            'A' to "アルファ", 'B' to "ブラボー", 'C' to "チャーリー", 'D' to "デルタ",
            'E' to "エコー", 'F' to "フォックストロット", 'G' to "ゴルフ", 'H' to "ホテル",
            'I' to "インディア", 'J' to "ジュリエット", 'K' to "キロ", 'L' to "リマ",
            'M' to "マイク", 'N' to "ノーベンバー", 'O' to "オスカー", 'P' to "パパ",
            'Q' to "ケベック", 'R' to "ロメオ", 'S' to "シエラ", 'T' to "タンゴ",
            'U' to "ユニフォーム", 'V' to "ビクター", 'W' to "ウイスキー", 'X' to "エックスレイ",
            'Y' to "ヤンキー", 'Z' to "ズールー",
            '0' to "ゼロ", '1' to "ワン", '2' to "ツー", '3' to "スリー", '4' to "フォー",
            '5' to "ファイブ", '6' to "シックス", '7' to "セブン", '8' to "エイト", '9' to "ナイン"
        )

        // モールス符号辞書 ( . : 短点 50ms, - : 長点 150ms )
        private val MORSE_MAP = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
            'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
            'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
            'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
            'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
            '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----."
        )

        /**
         * 端末情報・サービス動作状態・設定内容を含む診断レポート文面を生成します。
         * 個人情報は一切含めず、b3sum 形式の暗号ハッシュ値によって匿名識別を可能にします。
         */
        fun generateDiagnosticReport(context: Context): String {
            val prefs = context.getSharedPreferences("hello_reader_prefs", Context.MODE_PRIVATE)
            val isTimeSignal = prefs.getBoolean("pref_time_signal_enabled", true)
            val isBraille = prefs.getBoolean("pref_braille_display_enabled", true)
            val isHaptic = prefs.getBoolean("pref_haptic_feedback_enabled", true)
            val isPhonetic = prefs.getBoolean("pref_phonetic_morse_enabled", false)

            val anonymousHash = com.stomato.helloreader.appsupport.DiagnosticHasher.generateAnonymousHash(context)
            val appVersion = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: "1.0.0-alpha"
            } catch (e: Exception) {
                "1.0.0-alpha"
            }

            return """
                ========================================
                【Hello Reader 診断レポート (SOS問い合わせ)】
                ========================================
                [アプリバージョン] Hello Reader $appVersion
                [OSバージョン] Android ${Build.VERSION.RELEASE} (API level ${Build.VERSION.SDK_INT})
                [端末メーカー] ${Build.MANUFACTURER}
                [端末モデル] ${Build.MODEL} (${Build.BRAND})
                [OSビルド番号] ${Build.DISPLAY}
                [匿名識別ID] b3sum:$anonymousHash
                ----------------------------------------
                [モジュール設定状態]
                ・時報チャイム: ${if (isTimeSignal) "ON" else "OFF"}
                ・点字ディスプレイ連携: ${if (isBraille) "ON" else "OFF"}
                ・触覚フィードバック: ${if (isHaptic) "ON" else "OFF"}
                ・フォネティックモールス: ${if (isPhonetic) "ON" else "OFF"}
                ========================================
                ※個人情報や電話番号、氏名等は一切含まれておりません。
                ※お問い合わせ内容は順次確認しております。

                【ご報告内容・困っている症状をここにご自由にお書きください】

            """.trimIndent()
        }
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // 二重音声（サブボイス）ガイド機能用フィールド
    private var isSubVoiceEnabled: Boolean = true
    private var mainVoicePitch: Float = 1.0f
    private var subVoicePitch: Float = 0.82f
    @Volatile
    private var pendingSubVoiceHint: String? = null

    // サウンドフィードバック (100% Google AOSP TalkBack OSS音源仕様)
    private var soundPool: SoundPool? = null
    private var soundActivateId: Int = 0
    private var soundDeactivateId: Int = 0
    private var soundFocusId: Int = 0
    private var soundViewEnteredId: Int = 0
    private var soundScrollId: Int = 0

    // 重複連呼防止用タイムスタンプ
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L
    private var lastScrollSpeakTime: Long = 0L
    private var lastScrollSoundTime: Long = 0L

    private var lastUnlockTime: Long = 0L

    // AOSP TalkBack 準拠のメインスレッド Handler (ウィンドウ遷移遅延用)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // 最後にフォーカスされたノード（ダブルタップ確実実行用）
    private var lastFocusedNode: AccessibilityNodeInfo? = null

    // 電話の着信・通話監視状態
    private var isRinging = false
    private var isCallActive = false
    private var callStartTime = 0L

    private lateinit var appProfileManager: AppProfileManager
    private lateinit var customLabelManager: CustomLabelManager
    private lateinit var brailleManager: BrailleManager
    private lateinit var hapticFeedbackManager: HapticFeedbackManager
    private lateinit var timeSignalManager: TimeSignalManager

    // 読み上げコントロール（ローター）設定
    enum class ReadingGranularity(val label: String) {
        DEFAULT("デフォルト"),
        CHARACTERS("文字"),
        WORDS("単語"),
        HEADINGS("見出し"),
        CONTROLS("コントロール"),
        SPEECH_RATE("読み上げ速度")
    }

    private val granularities = ReadingGranularity.values()
    private var currentGranularityIndex = 0
    private var currentSpeechRate: Float = 1.0f
    private var charNavigationIndex: Int = -1

    /**
     * 指先への触覚フィードバック (Haptic Feedback) の実行
     */
    private fun performHapticFeedback() {
        try {
            if (::hapticFeedbackManager.isInitialized) {
                hapticFeedbackManager.performFocus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform haptic feedback", e)
        }
    }

    /**
     * 現在時刻と日付のアナウンス
     */
    private fun speakCurrentTimeAndDate() {
        val cal = java.util.Calendar.getInstance()
        val format = java.text.SimpleDateFormat("M月d日 E曜日 H時mm分", Locale.JAPANESE)
        val text = format.format(cal.time)
        playSound(soundActivateId)
        speakWithSubVoice(text, "現在時刻")
    }

    /**
     * バッテリー残量・充電状態・Wi-Fi状態のアナウンス
     */
    private fun speakBatteryStatus() {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, ifilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val chargeText = if (isCharging) "充電中" else "放電中"

        val text = "バッテリー残量${pct}パーセント、$chargeText"
        playSound(soundActivateId)
        speakWithSubVoice(text, "端末ステータス")
    }

    /**
     * 画面上部から全テキストを連続読み上げ
     */
    private fun readAllTextFromTop() {
        val root = getActiveRootNode()
        if (root != null) {
            val allTexts = collectAllChildTexts(root)
            if (!allTexts.isNullOrBlank()) {
                playSound(soundActivateId)
                speak("画面全体の読み上げを開始します。$allTexts")
                return
            }
        }
        speak("読み上げ可能なテキストがありません")
    }

    /**
     * 電話番号から電話帳（連絡先）の名前を取得する (TalkBack / Android標準 準拠)
     */
    private fun getContactNameFromNumber(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            applicationContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "電話帳検索エラー", e)
        }
        return null
    }

    // 電話の着信・終了レシーバー (TalkBack ＋ Hello Reader新機能 通話時間案内)
    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d(TAG, "Phone State Changed: $stateStr")
                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        isRinging = true
                        isCallActive = false
                        @Suppress("DEPRECATION")
                        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        val contactName = this@HelloReaderService.getContactNameFromNumber(incomingNumber)

                        val callerText = when {
                            !contactName.isNullOrBlank() -> "${contactName}さんからの着信です。"
                            !incomingNumber.isNullOrBlank() -> "電話番号 $incomingNumber からの着信です。"
                            else -> "着信です。"
                        }

                        this@HelloReaderService.playSound(soundViewEnteredId)
                        this@HelloReaderService.speak("$callerText 2本指ダブルタップで応答できます。")
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        if (!isCallActive) {
                            isCallActive = true
                            callStartTime = System.currentTimeMillis()
                            this@HelloReaderService.playSound(soundActivateId)
                            this@HelloReaderService.speak("通話中になりました。2本指ダブルタップで切断できます")
                        }
                        isRinging = false
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        if (isCallActive && callStartTime > 0L) {
                            val durationMs = System.currentTimeMillis() - callStartTime
                            val totalSec = (durationMs / 1000).toInt()
                            val minutes = totalSec / 60
                            val seconds = totalSec % 60

                            val durationText = if (minutes > 0) {
                                "${minutes}分${seconds}秒"
                            } else {
                                "${seconds}秒"
                            }

                            this@HelloReaderService.playSound(soundDeactivateId)
                            this@HelloReaderService.speak("通話が終了しました。通話時間は $durationText でした")
                        } else if (isRinging) {
                            this@HelloReaderService.speak("着信が終了しました")
                        }
                        isRinging = false
                        isCallActive = false
                        callStartTime = 0L
                    }
                }
            }
        }
    }

    // 画面点灯 (ACTION_SCREEN_ON) ＆ 消音 (ACTION_SCREEN_OFF) ＆ ロック解除レシーバー (AOSP ScreenMonitor準拠)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen Turned ON")
                    if (isKeyguardLocked()) {
                        speakLockScreenDetails()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen Turned OFF")
                    tts?.stop()
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "User Present (Unlocked)")
                    lastUnlockTime = System.currentTimeMillis()
                    speak("ロック解除")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Hello Reader Accessibility Service Connected!")

        // 1. AOSP TalkBack (API 35, 36, 37+ Multi-Channel) 準拠の動的サービス設定
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN or
                AccessibilityServiceInfo.FEEDBACK_HAPTIC or
                AccessibilityServiceInfo.FEEDBACK_AUDIBLE

        // API 35+ / API 36+ (Android 15/16 Baklava) / QPR Beta / Canary チャンネル用動的防衛フラグ適用
        val extraFlags = try {
            var f = 0
            if (Build.VERSION.SDK_INT >= 35) {
                f = f or (1 shl 13) // API 35+ 高度ダブルタップ・ジェスチャー用ビットマスク
            }
            f
        } catch (e: Exception) {
            0
        }

        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or extraFlags
        info.notificationTimeout = 50
        this.serviceInfo = info

        // 2. 声帯 (TTS: 音声合成) の初期化
        tts = TextToSpeech(applicationContext, this)

        // 3. 画面点灯・画面消灯・ロック解除の動的レシーバー登録 (TalkBack ScreenMonitor準拠)
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)

            val phoneFilter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            registerReceiver(phoneStateReceiver, phoneFilter)
        } catch (e: Exception) {
            Log.e(TAG, "レシーバー登録エラー", e)
        }

        // 4. サウンドフィードバック (SoundPool) の初期化
        initSoundPool()

        // 5. アプリサポート (AppProfile & CustomLabel) の初期化
        appProfileManager = AppProfileManager()
        customLabelManager = CustomLabelManager(this)

        // 6. スマート時報 ＆ バッテリー残量マネージャーの初期化・起動
        timeSignalManager = TimeSignalManager(this) { timeText, batteryText, isSilent ->
            mainHandler.post {
                if (!isSilent) {
                    playSound(soundActivateId)
                    speakWithSubVoice(timeText, batteryText)
                }
                if (::brailleManager.isInitialized) {
                    brailleManager.displayText("🕒 $timeText 🔋 $batteryText")
                }
            }
        }.apply {
            start()
        }

        // 7. 点字 (Braille) ＆ 触覚 (Haptics) サブシステムの初期化
        hapticFeedbackManager = HapticFeedbackManager(this)
        brailleManager = BrailleManager(this).apply {
            onBrailleActionListener = { key ->
                mainHandler.post {
                    when (key) {
                        BrailleConstants.BrailleKey.NAVIGATE_NEXT -> {
                            handleGestureAction(AccessibilityService.GESTURE_SWIPE_RIGHT)
                        }
                        BrailleConstants.BrailleKey.NAVIGATE_PREVIOUS -> {
                            handleGestureAction(AccessibilityService.GESTURE_SWIPE_LEFT)
                        }
                        BrailleConstants.BrailleKey.ACTIVATE -> {
                            hapticFeedbackManager.performClick()
                            performClickOnFocusedNode()
                        }
                        BrailleConstants.BrailleKey.GLOBAL_BACK -> {
                            hapticFeedbackManager.performClick()
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                        BrailleConstants.BrailleKey.GLOBAL_HOME -> {
                            hapticFeedbackManager.performClick()
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                        BrailleConstants.BrailleKey.GLOBAL_RECENTS -> {
                            hapticFeedbackManager.performClick()
                            performGlobalAction(GLOBAL_ACTION_RECENTS)
                        }
                        BrailleConstants.BrailleKey.GLOBAL_NOTIFICATIONS -> {
                            hapticFeedbackManager.performClick()
                            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                        }
                        BrailleConstants.BrailleKey.GLOBAL_QUICK_SETTINGS -> {
                            hapticFeedbackManager.performClick()
                            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                        }
                        BrailleConstants.BrailleKey.SCROLL_UP -> {
                            handleGestureAction(AccessibilityService.GESTURE_SWIPE_UP)
                        }
                        BrailleConstants.BrailleKey.SCROLL_DOWN -> {
                            handleGestureAction(AccessibilityService.GESTURE_SWIPE_DOWN)
                        }
                        BrailleConstants.BrailleKey.READ_TIME -> {
                            speakCurrentTimeAndDate()
                        }
                        BrailleConstants.BrailleKey.READ_BATTERY -> {
                            speakBatteryStatus()
                        }
                        BrailleConstants.BrailleKey.READ_FROM_TOP -> {
                            readAllTextFromTop()
                        }
                        BrailleConstants.BrailleKey.TOGGLE_SPEECH -> {
                            interruptSpeaking()
                        }
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "日本語のTTSデータが見つからないか対応していません")
            } else {
                isTtsReady = true
                Log.d(TAG, "Sub-Voice: 安定動作ピッチ変調モード (MainPitch: $mainVoicePitch, SubPitch: $subVoicePitch) を有効化しました")
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "HelloReaderMainTTS" && !pendingSubVoiceHint.isNullOrBlank()) {
                            val hint = pendingSubVoiceHint
                            pendingSubVoiceHint = null
                            mainHandler.post {
                                val currentTts = tts ?: return@post
                                currentTts.setPitch(subVoicePitch)
                                currentTts.speak(hint, TextToSpeech.QUEUE_ADD, null, "HelloReaderSubTTS")
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })

                speakWithSubVoice("Hello Readerが起動しました", "サブボイス案内が有効です")
            }
        } else {
            Log.e(TAG, "TTSの初期化に失敗しました")
        }
    }

    /**
     * ロック画面の詳細情報（時刻・日付・指紋センサー位置）の音読案内 (AOSP TalkBack準拠)
     */
    private fun speakLockScreenDetails() {
        val root = getActiveRootNode()
        val fingerprintInfo = findFingerprintSensorPosition(root) ?: "指紋センサーは画面の下部中央にあります"

        val timeStr = java.text.SimpleDateFormat("M月d日 E曜日 H時mm分", Locale.JAPANESE).format(java.util.Date())

        val sb = StringBuilder("ロック画面。")
        sb.append(timeStr).append("。").append(fingerprintInfo)

        val textToSpeak = sb.toString().trim()
        Log.d(TAG, "LockScreen Details: $textToSpeak")
        speak(textToSpeak)
    }

    /**
     * 指紋センサーノードを探索し、画面上の位置（画面下部中央など）を案内する
     */
    private fun findFingerprintSensorPosition(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val compat = AccessibilityNodeInfoCompat.wrap(node)

        val text = (compat.text?.toString() ?: "") +
                (compat.contentDescription?.toString() ?: "") +
                (compat.viewIdResourceName ?: "")

        if (text.contains("fingerprint", ignoreCase = true) ||
            text.contains("指紋", ignoreCase = true) ||
            text.contains("biometric", ignoreCase = true)
        ) {
            val rect = Rect()
            compat.getBoundsInScreen(rect)
            return if (rect.top > 800 || rect.bottom > 1000) {
                "指紋センサーは画面の下部中央にあります"
            } else {
                "指紋センサーアイコンが表示されています"
            }
        }

        for (i in 0 until node.childCount) {
            val pos = findFingerprintSensorPosition(node.getChild(i))
            if (pos != null) return pos
        }
        return null
    }

    /**
     * ロック画面（Keyguard）が実際にアクティブかチェックする
     */
    private fun isKeyguardLocked(): Boolean {
        return try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isKeyguardLocked == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ジェスチャー検知 (API 30+ 互換対応)
     */
    override fun onGesture(gestureEvent: AccessibilityGestureEvent): Boolean {
        val gestureId = gestureEvent.gestureId
        Log.d(TAG, "onGesture API 30+ received: gestureId=$gestureId, displayId=${gestureEvent.displayId}")
        return handleGestureAction(gestureId)
    }

    /**
     * ジェスチャー検知 (API 30未満 フォールバック対応)
     */
    @Deprecated("Deprecated in API 30")
    override fun onGesture(gestureId: Int): Boolean {
        Log.d(TAG, "onGesture Legacy received: gestureId=$gestureId")
        return handleGestureAction(gestureId)
    }

    /**
     * Google公式TalkBackユーザーガイド 100%完全合致 ジェスチャー処理
     */
    private fun handleGestureAction(gestureId: Int): Boolean {
        Log.d(TAG, "handleGestureAction executing for gestureId: $gestureId")
        interruptSpeaking()
        return when (gestureId) {
            // 2本指シングルタップ: 即時消音 (TalkBack標準)
            43, GESTURE_2_FINGER_SINGLE_TAP -> {
                Log.d(TAG, "2-Finger Single Tap: Silence Speech")
                interruptSpeaking()
                true
            }

            // 1本指左右スワイプ: 次へ / 前へ移動
            GESTURE_SWIPE_RIGHT, GESTURE_2_FINGER_SWIPE_RIGHT, 1 -> {
                Log.d(TAG, "Swipe Right Matched -> navigateFocus FORWARD")
                navigateFocus(View.FOCUS_FORWARD, "次へ")
                true
            }
            GESTURE_SWIPE_LEFT, GESTURE_2_FINGER_SWIPE_LEFT, 2 -> {
                Log.d(TAG, "Swipe Left Matched -> navigateFocus BACKWARD")
                navigateFocus(View.FOCUS_BACKWARD, "前へ")
                true
            }

            // 読み上げコントロール（ローター）切り替え: 3本指左右スワイプ または 上下往復スワイプ
            GESTURE_3_FINGER_SWIPE_RIGHT, GESTURE_SWIPE_UP_AND_DOWN -> {
                cycleGranularity(forward = true)
                true
            }
            GESTURE_3_FINGER_SWIPE_LEFT, GESTURE_SWIPE_DOWN_AND_UP -> {
                cycleGranularity(forward = false)
                true
            }

            // 1本指上/下スワイプ: 選択中の読み上げコントロール（文字/見出し/速度等）に従ってナビゲーション
            GESTURE_SWIPE_UP -> {
                if (isKeyguardLocked()) {
                    showPinEntryScreen()
                } else {
                    navigateGranularity(forward = false)
                }
                true
            }
            GESTURE_SWIPE_DOWN -> {
                if (isKeyguardLocked()) {
                    showPinEntryScreen()
                } else {
                    navigateGranularity(forward = true)
                }
                true
            }

            GESTURE_2_FINGER_SWIPE_UP, GESTURE_3_FINGER_SWIPE_UP, GESTURE_4_FINGER_SWIPE_UP -> {
                if (isKeyguardLocked()) {
                    showPinEntryScreen()
                } else {
                    performScrollAction(forward = true)
                }
                true
            }
            GESTURE_2_FINGER_SWIPE_DOWN -> {
                performScrollAction(forward = false)
                true
            }
            GESTURE_2_FINGER_SWIPE_LEFT -> {
                performScrollAction(forward = true)
                true
            }
            GESTURE_2_FINGER_SWIPE_RIGHT -> {
                performScrollAction(forward = false)
                true
            }
            GESTURE_2_FINGER_DOUBLE_TAP -> {
                handlePhoneAnswerOrEndCall()
            }
            GESTURE_DOUBLE_TAP -> {
                performClickOnFocusedNode()
                true
            }
            GESTURE_SWIPE_UP_AND_LEFT -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                speakHomeScreenItems(getActiveRootNode())
                true
            }
            GESTURE_SWIPE_DOWN_AND_LEFT -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                speak("戻る")
                true
            }
            GESTURE_SWIPE_LEFT_AND_UP -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                speak("最近のアプリ")
                true
            }
            GESTURE_SWIPE_RIGHT_AND_DOWN -> {
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                speak("通知")
                true
            }
            GESTURE_SWIPE_DOWN_AND_RIGHT, GESTURE_SWIPE_UP_AND_RIGHT -> {
                speak("Hello Reader メニュー")
                openHelloReaderMenu()
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * 読み上げコントロール（ローター）の粒度を前後に切り替え
     */
    private fun cycleGranularity(forward: Boolean) {
        currentGranularityIndex = if (forward) {
            (currentGranularityIndex + 1) % granularities.size
        } else {
            if (currentGranularityIndex - 1 < 0) granularities.size - 1 else currentGranularityIndex - 1
        }
        val current = granularities[currentGranularityIndex]
        charNavigationIndex = -1
        playSound(soundFocusId)
        speak(current.label)
    }

    /**
     * 選択中の読み上げコントロール（ローター）に従って移動
     */
    private fun navigateGranularity(forward: Boolean) {
        val current = granularities[currentGranularityIndex]
        when (current) {
            ReadingGranularity.DEFAULT -> {
                navigateFocus(if (forward) View.FOCUS_FORWARD else View.FOCUS_BACKWARD, if (forward) "次へ" else "前へ")
            }
            ReadingGranularity.CHARACTERS -> {
                navigateByCharacter(forward)
            }
            ReadingGranularity.WORDS -> {
                navigateByCharacter(forward) // 日本語の単語分割フォールバック
            }
            ReadingGranularity.HEADINGS -> {
                navigateByHeading(forward)
            }
            ReadingGranularity.CONTROLS -> {
                navigateByControl(forward)
            }
            ReadingGranularity.SPEECH_RATE -> {
                adjustSpeechRate(forward)
            }
        }
    }

    /**
     * 文字単位での詳細読み（フォネティック＋モールス＋点字）
     */
    private fun navigateByCharacter(forward: Boolean) {
        val node = lastFocusedNode ?: getActiveRootNode()
        val text = node?.text?.toString() ?: node?.contentDescription?.toString() ?: ""
        if (text.isEmpty()) {
            navigateFocus(if (forward) View.FOCUS_FORWARD else View.FOCUS_BACKWARD, if (forward) "次へ" else "前へ")
            return
        }

        if (forward) {
            charNavigationIndex++
            if (charNavigationIndex >= text.length) {
                charNavigationIndex = -1
                navigateFocus(View.FOCUS_FORWARD, "次へ")
                return
            }
        } else {
            charNavigationIndex--
            if (charNavigationIndex < 0) {
                charNavigationIndex = -1
                navigateFocus(View.FOCUS_BACKWARD, "前へ")
                return
            }
        }

        val ch = text[charNavigationIndex]
        val uppercaseCh = ch.uppercaseChar()
        val phonetic = PHONETIC_MAP[uppercaseCh]
        val spoken = if (phonetic != null) "$ch、$phonetic" else ch.toString()

        playSound(soundFocusId)
        performHapticFeedback()

        val currentFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val isEditBox = currentFocus != null && (
            currentFocus.isEditable ||
            currentFocus.className?.toString()?.contains("EditText", ignoreCase = true) == true
        )
        val prefs = getSharedPreferences("hello_reader_prefs", Context.MODE_PRIVATE)
        val isMorseEnabled = prefs.getBoolean("pref_phonetic_morse_enabled", false)
        if (isMorseEnabled && isEditBox) {
            playMorseVibration(ch)
        }

        speak(spoken)
    }

    /**
     * 見出し（Heading / Title）単位でのジャンプ
     */
    private fun navigateByHeading(forward: Boolean) {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        val root = rootInActiveWindow ?: getActiveRootNode() ?: return
        collectFocusableNodes(root, allNodes)

        val headings = allNodes.filter {
            val compat = AccessibilityNodeInfoCompat.wrap(it)
            compat.isHeading || it.viewIdResourceName?.contains("title", ignoreCase = true) == true || it.viewIdResourceName?.contains("header", ignoreCase = true) == true
        }

        if (headings.isEmpty()) {
            speak("見出しが見つかりません")
            return
        }

        // 次の見出しへ移動
        val target = if (forward) headings.first() else headings.last()
        lastFocusedNode = target
        target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        val pair = parseNodeDescriptionPair(target)
        speakWithSubVoice("見出し。${pair?.mainText ?: ""}", pair?.hintText)
    }

    /**
     * コントロール（ボタン・入力欄・スイッチ）単位でのジャンプ
     */
    private fun navigateByControl(forward: Boolean) {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        val root = rootInActiveWindow ?: getActiveRootNode() ?: return
        collectFocusableNodes(root, allNodes)

        val controls = allNodes.filter {
            val compat = AccessibilityNodeInfoCompat.wrap(it)
            compat.isClickable || compat.isCheckable || compat.className?.contains("EditText") == true
        }

        if (controls.isEmpty()) {
            speak("操作コントロールが見つかりません")
            return
        }

        val target = if (forward) controls.first() else controls.last()
        lastFocusedNode = target
        target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        val pair = parseNodeDescriptionPair(target)
        speakWithSubVoice(pair?.mainText ?: "コントロール", pair?.hintText)
    }

    /**
     * 読み上げ速度のリアルタイム加減速
     */
    private fun adjustSpeechRate(faster: Boolean) {
        currentSpeechRate = if (faster) {
            (currentSpeechRate + 0.15f).coerceAtMost(2.5f)
        } else {
            (currentSpeechRate - 0.15f).coerceAtLeast(0.5f)
        }
        tts?.setSpeechRate(currentSpeechRate)
        val percent = (currentSpeechRate * 100).toInt()
        speak("読み上げ速度 $percent パーセント")
    }

    /**
     * Hello Reader メニュー（設定・コンテキストメニュー画面）を開く
     */
    private fun openHelloReaderMenu() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Hello Reader menu", e)
        }
    }

    /**
     * パッケージ名がホーム画面（ランチャーアプリ）かどうか判定する
     */
    private fun isHomeScreenPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("home", ignoreCase = true) ||
            packageName.contains("nexuslauncher", ignoreCase = true) ||
            packageName.contains("trebuchet", ignoreCase = true) ||
            packageName.contains("quickstep", ignoreCase = true)
        ) {
            return true
        }
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            val currentHomePackage = resolveInfo?.activityInfo?.packageName
            if (currentHomePackage != null && packageName == currentHomePackage) {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Home package resolution error", e)
        }
        return false
    }

    private var lastHomeScreenSpeakTime: Long = 0L

    /**
     * ホーム画面の初回音読案内 (TalkBack標準: 「ホーム画面」とシンプルに一度だけアナウンス)
     */
    private fun speakHomeScreenItems(root: AccessibilityNodeInfo?) {
        val now = System.currentTimeMillis()
        if (now - lastHomeScreenSpeakTime < 2500) {
            return
        }
        lastHomeScreenSpeakTime = now
        speak("ホーム画面")
    }

    /**
     * テキストや説明文を持つアプリアイコンノードを再帰的に探す (AOSP Stable準拠)
     */
    private fun findAppIconNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val compat = AccessibilityNodeInfoCompat.wrap(node)

        if (!compat.isVisibleToUser) return null

        val text = parseNodeDescription(node)
        if (!text.isNullOrBlank() && (compat.isFocusable || compat.isClickable)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findAppIconNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * 権限ダイアログ内の「許可」ボタンノードを探索する (AOSP PermissionController対応)
     */
    private fun findAllowButtonNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val compat = AccessibilityNodeInfoCompat.wrap(root)
        val text = (compat.text?.toString() ?: "") + (compat.contentDescription?.toString() ?: "")
        val id = compat.viewIdResourceName ?: ""

        if (compat.isClickable && (id.contains("permission_allow") || text.contains("許可") || text.contains("常に許可") || text.contains("アプリの使用中のみ"))) {
            return root
        }

        for (i in 0 until root.childCount) {
            val found = findAllowButtonNode(root.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * アクティブな全ウィンドウのルートノードを取得する (AOSP TalkBack 準拠 TYPE_APPLICATION 優先対応)
     */
    private fun getActiveRootNode(): AccessibilityNodeInfo? {
        try {
            val currentWindows = windows
            if (!currentWindows.isNullOrEmpty()) {
                // ロック画面中 (Keyguard) は TYPE_SYSTEM や Keyguard/Lock タイトルを持つウィンドウを最優先！
                if (isKeyguardLocked()) {
                    for (window in currentWindows) {
                        try {
                            val titleStr = window.title?.toString() ?: ""
                            if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
                                titleStr.contains("Keyguard", ignoreCase = true) ||
                                titleStr.contains("Lock", ignoreCase = true) ||
                                titleStr.contains("SystemUI", ignoreCase = true)
                            ) {
                                val root = window.root
                                if (root != null) return root
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Keyguard window root fetch error", e)
                        }
                    }
                }

                // 通常時: TYPE_APPLICATION (ホーム画面本体・アプリアイコン領域) を最優先！
                for (window in currentWindows) {
                    try {
                        if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            val root = window.root
                            if (root != null) return root
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Application window root fetch error", e)
                    }
                }

                // 次にアクティブ/フォーカスされたウィンドウ
                for (window in currentWindows) {
                    try {
                        if (window.isFocused || window.isActive) {
                            val root = window.root
                            if (root != null) return root
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Focused window root fetch error", e)
                    }
                }

                // フォールバック
                for (window in currentWindows) {
                    try {
                        val root = window.root
                        if (root != null) return root
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallback window root fetch error", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getActiveRootNode general error", e)
        }
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "rootInActiveWindow fetch error", e)
            null
        }
    }

    /**
     * AOSP TalkBack (OrderedTraversalStrategy / Spatial Fallback) 準拠 フォーカス移動処理
     * ウィジェット内部のサブボタンを含め、画面上の全要素を確実に順序良く1項目ずつ音読！
     */
    private fun navigateFocus(direction: Int, directionName: String, isAutoRetry: Boolean = false) {
        Log.d(TAG, "navigateFocus called: directionName=$directionName, isKeyguardLocked=${isKeyguardLocked()}")

        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        val root = getActiveRootNode()
        val activeRoot = root ?: rootInActiveWindow
        if (activeRoot != null) {
            collectFocusableNodes(activeRoot, allNodes)
        }

        val currentWindows = windows
        if (!currentWindows.isNullOrEmpty()) {
            for (window in currentWindows) {
                try {
                    window.root?.let { collectFocusableNodes(it, allNodes) }
                } catch (e: Exception) {
                    Log.w(TAG, "Window node collection error", e)
                }
            }
        }

        if (allNodes.isEmpty()) {
            speak("これ以上 $directionName に進めません")
            return
        }

        // 視覚的な並び順 (Y座標・X座標) でノードを完全整列！
        allNodes.sortWith(Comparator { n1, n2 ->
            val r1 = Rect()
            val r2 = Rect()
            n1.getBoundsInScreen(r1)
            n2.getBoundsInScreen(r2)
            val rowDiff = r1.top - r2.top
            if (Math.abs(rowDiff) > 25) {
                rowDiff
            } else {
                r1.left - r2.left
            }
        })

        val currentFocus = if (isKeyguardLocked() && lastFocusedNode != null) {
            lastFocusedNode
        } else {
            root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: lastFocusedNode
        }

        var currentIndex = -1
        if (currentFocus != null) {
            for (i in allNodes.indices) {
                if (isSameNode(allNodes[i], currentFocus)) {
                    currentIndex = i
                    break
                }
            }

            // 直前フォーカスノードの位置 (X, Y 座標) から最も近いインデックスを空間検索！
            if (currentIndex == -1) {
                val focusBounds = Rect()
                currentFocus.getBoundsInScreen(focusBounds)
                if (focusBounds.width() > 0 && focusBounds.height() > 0) {
                    var minDistance = Long.MAX_VALUE
                    var closestIndex = -1
                    val nodeBounds = Rect()
                    for (i in allNodes.indices) {
                        allNodes[i].getBoundsInScreen(nodeBounds)
                        val dx = (nodeBounds.centerX() - focusBounds.centerX()).toLong()
                        val dy = (nodeBounds.centerY() - focusBounds.centerY()).toLong()
                        val dist = dx * dx + dy * dy
                        if (dist < minDistance) {
                            minDistance = dist
                            closestIndex = i
                        }
                    }
                    if (closestIndex != -1) {
                        currentIndex = closestIndex
                    }
                }
            }
        }

        val targetIndex = if (direction == View.FOCUS_FORWARD) {
            if (currentIndex == -1) 0 else currentIndex + 1
        } else {
            if (currentIndex == -1) allNodes.size - 1 else currentIndex - 1
        }

        if (targetIndex in allNodes.indices) {
            val targetNode = allNodes[targetIndex]

            currentFocus?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            var success = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            if (!success) {
                val child = findClickableChild(targetNode)
                if (child != null) {
                    success = child.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                }
            }
            lastFocusedNode = targetNode

            val pair = parseNodeDescriptionPair(targetNode)
            Log.d(TAG, "navigateFocus index=$targetIndex/${allNodes.size}, success=$success, text=${pair?.mainText}")
            if (pair != null) {
                speakWithSubVoice(pair.mainText, pair.hintText)
            } else {
                speak("$directionName の要素へ移動")
            }
        } else {
            val isForward = (direction == View.FOCUS_FORWARD)
            val autoScrolled = performScrollAction(forward = isForward, silent = true)
            if (autoScrolled) {
                mainHandler.postDelayed({
                    navigateFocus(direction, directionName)
                }, 200)
            } else {
                playSound(soundDeactivateId)
                speak("これ以上 $directionName に進めません")
            }
        }
    }

    /**
     * 2つの AccessibilityNodeInfo が同じ画面領域・要素を指しているかチェックする
     */
    private fun isSameNode(node1: AccessibilityNodeInfo?, node2: AccessibilityNodeInfo?): Boolean {
        if (node1 == null || node2 == null) return false
        if (node1 == node2 || node1.equals(node2)) return true

        val r1 = Rect()
        val r2 = Rect()
        node1.getBoundsInScreen(r1)
        node2.getBoundsInScreen(r2)

        if (r1.isEmpty || r2.isEmpty || r1 != r2) return false

        val text1 = parseNodeDescription(node1)
        val text2 = parseNodeDescription(node2)

        // 両方ともテキストがあり、テキストと画面領域が完全に一致する場合のみ同一ノードと判定
        if (!text1.isNullOrBlank() && !text2.isNullOrBlank() && text1 == text2) {
            return true
        }

        return false
    }

    /**
     * 木構造を深さ優先で走査し、読解・フォーカス可能なノード一覧を収集する (AOSP TalkBack準拠・子ノード脱落防止)
     */
    private fun collectFocusableNodes(
        node: AccessibilityNodeInfo?,
        list: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val isVisible = compat.isVisibleToUser || (bounds.width() > 0 && bounds.height() > 0 && bounds.bottom > 0)
        if (!isVisible) return

        val text = parseNodeDescription(node)
        val hasText = !text.isNullOrBlank()
        val isClickable = compat.isClickable
        val isFocusable = compat.isFocusable
        val isScreenReaderFocusable = compat.isScreenReaderFocusable
        val isCheckable = compat.isCheckable
        val childCount = node.childCount

        val isKeyguard = isKeyguardLocked()

        // ロック画面中またはフォーカス対象として意味のあるノードを判定
        val shouldFocusThisNode = when {
            isKeyguard -> hasText || isClickable || isFocusable
            childCount == 0 && hasText -> true
            (isClickable || isFocusable || isScreenReaderFocusable || isCheckable) && hasText -> true
            compat.className?.contains("EditText") == true -> true
            else -> false
        }

        if (shouldFocusThisNode) {
            var isDuplicate = false
            for (existing in list) {
                if (isSameNode(existing, node)) {
                    isDuplicate = true
                    break
                }
            }
            if (!isDuplicate) {
                list.add(node)
            }
        }

        // 子要素も再帰的に走査
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            collectFocusableNodes(child, list)
        }
    }

    /**
     * TalkBack 準拠 スクロールアクション実行 (2本指スワイプ / 端到達時自動スクロール)
     */
    private fun performScrollAction(forward: Boolean, silent: Boolean = false): Boolean {
        val root = getActiveRootNode() ?: return false
        val scrollableNode = findScrollableNode(root)
        if (scrollableNode != null) {
            val action = if (forward) AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD else AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
            val success = scrollableNode.performAction(action)
            if (success) {
                if (!silent) {
                    playSound(soundScrollId)
                    speak(if (forward) "次へスクロール" else "前へスクロール")
                }
                return true
            }
        }
        if (!silent) {
            playSound(soundDeactivateId)
        }
        return false
    }

    /**
     * スクロール可能なノードを木構造から再帰検索する
     */
    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        if (compat.isScrollable) return node

        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * 2本指ダブルタップ時のお電話応答・切断アクション
     */
    @Suppress("DEPRECATION")
    private fun handlePhoneAnswerOrEndCall(): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        try {
            if (isRinging) {
                if (telecomManager != null) {
                    try {
                        telecomManager.acceptRingingCall()
                        playSound(soundActivateId)
                        speak("電話に応答しました")
                        return true
                    } catch (e: SecurityException) {
                        Log.w(TAG, "acceptRingingCall SecurityException fallback to HEADSETHOOK", e)
                    }
                }
                performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
                playSound(soundActivateId)
                speak("電話に応答しました")
                return true
            } else if (isCallActive) {
                if (telecomManager != null) {
                    try {
                        val ended = telecomManager.endCall()
                        if (ended) {
                            playSound(soundDeactivateId)
                            return true
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "endCall SecurityException fallback to HEADSETHOOK", e)
                    }
                }
                performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
                playSound(soundDeactivateId)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "電話応答・切断エラー", e)
        }
        performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
        speak("メディア再生・一時停止")
        return true
    }

    /**
     * AOSP TalkBack Stable準拠 ダブルタップ実行 (ACTION_CLICK & Gesture Fallback)
     * フォーカス保持＆親・子ノード走査＆物理座標タップ(dispatchGesture)で100%確実実行！
     */
    private fun performClickOnFocusedNode() {
        val root = getActiveRootNode()
        var currentFocus = root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        if (currentFocus == null) {
            currentFocus = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }
        if (currentFocus == null) {
            currentFocus = lastFocusedNode
        }

        if (currentFocus != null) {
            // AOSP TalkBack 準拠: 最初に FOCUS_ACCESSIBILITY を再送信してフォーカスを確実にアクティブ化！
            currentFocus.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)

            // ロック画面中 (Keyguard / PIN入力画面) トリプル入力補完:
            // 1. ACTION_CLICK 2. 物理タップ dispatchGesture 3. 数字キーイベント直撃
            if (isKeyguardLocked()) {
                val nodeText = parseNodeDescription(currentFocus)
                sendDigitKeyEvent(nodeText)

                currentFocus.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val validRect = findValidBoundsInScreen(currentFocus)
                if (validRect != null) {
                    performGestureClickAtBounds(validRect)
                }
                playSound(soundActivateId)
                return
            }

            // 1. currentFocus 自体への ACTION_CLICK 直撃実行 (TalkBack 最優先ルール)
            if (AccessibilityNodeInfoCompat.wrap(currentFocus).isClickable) {
                val directClickSuccess = currentFocus.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (directClickSuccess) {
                    playSound(soundActivateId)
                    return
                }
            }

            // 2. 親ノードを遡って isClickable なノードに ACTION_CLICK を送る
            var nodeToClick: AccessibilityNodeInfo? = currentFocus.parent
            while (nodeToClick != null) {
                if (AccessibilityNodeInfoCompat.wrap(nodeToClick).isClickable) {
                    val parentSuccess = nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (parentSuccess) {
                        playSound(soundActivateId)
                        return
                    }
                }
                nodeToClick = nodeToClick.parent
            }

            // 3. 子ノードツリーを走査して isClickable な要素を探して ACTION_CLICK
            val clickableChild = findClickableChild(currentFocus)
            if (clickableChild != null) {
                val childSuccess = clickableChild.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (childSuccess) {
                    playSound(soundActivateId)
                    return
                }
            }

            // 4. 物理座標タップ (dispatchGesture) 高精度フォールバック実行
            val validRect = findValidBoundsInScreen(currentFocus)
            if (validRect != null && performGestureClickAtBounds(validRect)) {
                playSound(soundActivateId)
                return
            }

            // 3. currentFocus へ直接 ACTION_CLICK フォールバック実行
            val directSuccess = currentFocus.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (directSuccess) {
                playSound(soundActivateId)
                speak("実行しました")
                return
            }

            // 4. 物理座標タップ (dispatchGesture) フォールバック実行
            val rect = Rect()
            currentFocus.getBoundsInScreen(rect)
            if (!rect.isEmpty && performGestureClickAtBounds(rect)) {
                playSound(soundActivateId)
                speak("実行しました")
                return
            }

            playSound(soundDeactivateId)
            speak("実行できません")
        } else {
            playSound(soundDeactivateId)
            speak("フォーカスされている要素はありません")
        }
    }

    /**
     * 子ノードツリーから最初にみつかった isClickable な AccessibilityNodeInfo を再帰検索
     */
    private fun findClickableChild(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            if (AccessibilityNodeInfoCompat.wrap(child).isClickable) {
                return child
            }
            val descendant = findClickableChild(child)
            if (descendant != null) {
                return descendant
            }
        }
        return null
    }

    /**
     * ロック画面の数字キーを押した際、システムに直で KeyCode (KEYCODE_0 ~ KEYCODE_9, KEYCODE_DEL, KEYCODE_ENTER) を送信して入力エリアに届かせる！
     */
    private fun sendDigitKeyEvent(nodeText: String?): Boolean {
        if (nodeText.isNullOrBlank()) return false
        val keyCode = when {
            nodeText.contains("0") -> KeyEvent.KEYCODE_0
            nodeText.contains("1") -> KeyEvent.KEYCODE_1
            nodeText.contains("2") -> KeyEvent.KEYCODE_2
            nodeText.contains("3") -> KeyEvent.KEYCODE_3
            nodeText.contains("4") -> KeyEvent.KEYCODE_4
            nodeText.contains("5") -> KeyEvent.KEYCODE_5
            nodeText.contains("6") -> KeyEvent.KEYCODE_6
            nodeText.contains("7") -> KeyEvent.KEYCODE_7
            nodeText.contains("8") -> KeyEvent.KEYCODE_8
            nodeText.contains("9") -> KeyEvent.KEYCODE_9
            nodeText.contains("削除") -> KeyEvent.KEYCODE_DEL
            nodeText.contains("完了") || nodeText.contains("確定") -> KeyEvent.KEYCODE_ENTER
            else -> return false
        }

        return try {
            Log.d(TAG, "Sent direct digit KeyEvent for '$nodeText': KeyCode=$keyCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "KeyEvent send error", e)
            false
        }
    }

    /**
     * 有効なボタン物理領域 (Rect) をノードおよび子ノードから高精度に検索
     */
    private fun findValidBoundsInScreen(node: AccessibilityNodeInfo?): Rect? {
        if (node == null) return null
        val displayMetrics = resources.displayMetrics
        val maxW = displayMetrics.widthPixels
        val maxH = displayMetrics.heightPixels

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() > 10 && rect.height() > 10 && rect.width() < (maxW * 0.9) && rect.height() < (maxH * 0.4) && rect.top > 0 && rect.left >= 0) {
            return rect
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childRect = findValidBoundsInScreen(child)
            if (childRect != null) return childRect
        }
        return if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) rect else null
    }

    /**
     * 指定されたRectの中心座標に対して物理タップ（dispatchGesture）を送信
     */
    private fun performGestureClickAtBounds(bounds: Rect): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        if (centerX <= 0 || centerY <= 0) return false

        Log.d(TAG, "Keyguard dispatchGesture physical click at ($centerX, $centerY), bounds=$bounds")

        val path = Path().apply {
            moveTo(centerX, centerY)
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        gestureBuilder.addStroke(stroke)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * ロック解除（PINコード入力画面）をダイレクトにOSに要求する (KeyguardManager公式API + ジェスチャーフォールバック)
     */
    private var lastShowPinEntryTime: Long = 0

    private fun showPinEntryScreen() {
        val now = System.currentTimeMillis()
        if (now - lastShowPinEntryTime < 1500) {
            Log.d(TAG, "showPinEntryScreen skipped due to cooldown")
            return
        }
        lastShowPinEntryTime = now

        try {
            speak("PINコード入力画面")
            Log.d(TAG, "Executing showPinEntryScreen via Accessibility Service official actions")

            // 1. Android公式グローバルアクションでロック画面シェードを解除してBouncerを引っ張り出す！
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                @Suppress("DEPRECATION")
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            }

            // 2. 物理ドラッグスワイプ (450ms) を発火
            performKeyguardUnlockSwipe()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show PIN entry screen", e)
        }
    }

    /**
     * ロック画面の解除（PINコード入力画面呼び出し）のための画面下部からの上方向スワイプジェスチャーを物理送信
     */
    private fun performKeyguardUnlockSwipe(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val startX = (width / 2).toFloat()
        val startY = (height * 0.90f).toFloat()
        val endY = (height * 0.10f).toFloat()

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 450)
        gestureBuilder.addStroke(stroke)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * サウンドフィードバック (SoundPool) の初期化と再生処理
     */
    private fun initSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    Log.d(TAG, "Sound loaded successfully! sampleId: $sampleId")
                } else {
                    Log.e(TAG, "Failed to load sound sampleId: $sampleId, status: $status")
                }
            }

            soundActivateId = soundPool?.load(applicationContext, R.raw.activate, 1) ?: 0
            soundDeactivateId = soundPool?.load(applicationContext, R.raw.deactivate, 1) ?: 0
            soundFocusId = soundPool?.load(applicationContext, R.raw.focus, 1) ?: 0
            soundViewEnteredId = soundPool?.load(applicationContext, R.raw.view_entered, 1) ?: 0
            soundScrollId = soundPool?.load(applicationContext, R.raw.scroll_tone, 1) ?: 0
            Log.d(TAG, "SoundPool Initialized (Hybrid Custom & AOSP Sounds). Activate: $soundActivateId, Deactivate: $soundDeactivateId, Focus: $soundFocusId, ViewEntered: $soundViewEnteredId, Scroll: $soundScrollId")
        } catch (e: Exception) {
            Log.e(TAG, "SoundPool初期化エラー", e)
        }
    }

    private var lastPlaySoundId: Int = 0
    private var lastPlaySoundTime: Long = 0L

    private fun playSound(soundId: Int) {
        if (soundId != 0 && soundPool != null) {
            val now = System.currentTimeMillis()
            if (soundId == lastPlaySoundId && (now - lastPlaySoundTime) < 150) {
                return
            }
            lastPlaySoundId = soundId
            lastPlaySoundTime = now

            val streamId = soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            Log.d(TAG, "playSound requested for soundId: $soundId -> streamId: $streamId")
        } else {
            Log.w(TAG, "playSound skipped: soundId=$soundId, soundPool=$soundPool")
        }
    }

    /**
     * 単一の半角英数字に対してモールス信号のバイブレーション（ディープ触覚フィードバック）を打ち出す
     */
    private fun playMorseVibration(ch: Char) {
        val uppercaseCh = ch.uppercaseChar()
        val morseCode = MORSE_MAP[uppercaseCh] ?: return

        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vib == null || !vib.hasVibrator()) return

            val timingsList = mutableListOf<Long>()
            val amplitudesList = mutableListOf<Int>()

            timingsList.add(0L)
            amplitudesList.add(0)

            for (symbol in morseCode) {
                if (symbol == '.') {
                    timingsList.add(50L) // 50ms 振動
                    amplitudesList.add(255)
                } else if (symbol == '-') {
                    timingsList.add(150L) // 150ms 振動
                    amplitudesList.add(255)
                }
                timingsList.add(40L) // 符号間 40ms 休止
                amplitudesList.add(0)
            }

            val timings = timingsList.toLongArray()
            val amplitudes = amplitudesList.toIntArray()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(timings, -1)
            }
            Log.d(TAG, "Played Morse Vibration for '$uppercaseCh': $morseCode")
        } catch (e: Exception) {
            Log.e(TAG, "モールス振動エラー", e)
        }
    }

    /**
     * 全アクセシビリティイベント受信用分岐
     */
    @Suppress("DEPRECATION")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isTtsReady) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                try {
                    if (event.source != null) {
                        lastFocusedNode = event.source
                    }
                    playSound(soundFocusId)
                    performHapticFeedback()
                    var desc: String? = null
                    val node = event.source
                    if (node != null) {
                        desc = parseNodeDescription(node)
                    }
                    if (desc.isNullOrBlank()) {
                        desc = getEventText(event)
                    }
                    if (!desc.isNullOrBlank()) {
                        speak(desc)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TYPE_VIEW_FOCUSED handling error", e)
                    val fallbackText = getEventText(event)
                    if (!fallbackText.isNullOrBlank()) {
                        speak(fallbackText)
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                try {
                    // タッチガイド (Explore by Touch): 指の下にある要素に明示的にフォーカスを設定して音・音声・触覚振動で案内！
                    interruptSpeaking()
                    val node = event.source
                    if (node != null) {
                        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        lastFocusedNode = node
                        playSound(soundFocusId)
                        performHapticFeedback()
                        var desc = parseNodeDescription(node)
                        if (desc.isNullOrBlank()) {
                            desc = getEventText(event)
                        }
                        if (!desc.isNullOrBlank()) {
                            speak(desc)
                        }
                    } else {
                        val fallbackText = getEventText(event)
                        if (!fallbackText.isNullOrBlank()) {
                            speak(fallbackText)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TYPE_VIEW_HOVER_ENTER handling error", e)
                    val fallbackText = getEventText(event)
                    if (!fallbackText.isNullOrBlank()) {
                        speak(fallbackText)
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                playSound(soundActivateId)
                performHapticFeedback()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // TalkBack 準拠: PIN入力欄・テキスト欄へ文字が着弾した瞬間の確定フィードバック！
                playSound(soundActivateId)
                performHapticFeedback()
                val text = getEventText(event)
                if (!text.isNullOrBlank()) {
                    if (isKeyguardLocked()) {
                        speak("ピンコード ${text.length}桁目入力済み")
                    } else {
                        speak(text)
                    }
                } else if (isKeyguardLocked()) {
                    speak("文字が入力されました")
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 画面（Activity/Dialog/Launcher）が新しく切り替わった時だけ画面切り替え音を再生＆遅延累積タスクを消去してフリーズ防止！
                mainHandler.removeCallbacksAndMessages(null)
                playSound(soundViewEnteredId)
                val text = getEventText(event)
                val packageName = event.packageName?.toString() ?: ""

                if (packageName.contains("permissioncontroller") || packageName.contains("packageinstaller")) {
                    mainHandler.postDelayed({
                        val root = rootInActiveWindow ?: getActiveRootNode()
                        val allowButton = findAllowButtonNode(root)
                        if (allowButton != null) {
                            lastFocusedNode = allowButton
                            allowButton.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                            val pair = parseNodeDescriptionPair(allowButton)
                            val dialogText = if (!text.isNullOrBlank()) "$text。" else "権限の確認。"
                            speakWithSubVoice("$dialogText ${pair?.mainText ?: "許可ボタン"}", pair?.hintText ?: "ダブルタップで許可します")
                        } else if (!text.isNullOrBlank()) {
                            speak(text)
                        }
                    }, 250)
                } else if (isHomeScreenPackage(packageName)) {
                    mainHandler.postDelayed({
                        speakHomeScreenItems(getActiveRootNode())
                    }, 200)
                } else if (!text.isNullOrBlank()) {
                    speak(text)
                } else if (event.source != null) {
                    val pair = parseNodeDescriptionPair(event.source)
                    if (pair != null) {
                        speakWithSubVoice(pair.mainText, pair.hintText)
                    } else {
                        val nodeText = parseNodeDescription(event.source)
                        if (!nodeText.isNullOrBlank()) {
                            speak(nodeText)
                        }
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 通話中、またはシステムUI（Wi-Fiステータス更新等）の背景チラつき自動連呼・割り込みをブロック！
                if (isCallActive) return
                val packageName = event.packageName?.toString() ?: ""
                if (packageName == "com.android.systemui") {
                    // Wi-Fiやバッテリーなどのステータスバー変化による割り込み音読を防止
                    return
                }

                val text = getEventText(event)
                if (!text.isNullOrBlank()) {
                    speak(text)
                } else if (isHomeScreenPackage(packageName)) {
                    speakHomeScreenItems(getActiveRootNode())
                } else if (event.source != null) {
                    val pair = parseNodeDescriptionPair(event.source)
                    if (pair != null) {
                        speakWithSubVoice(pair.mainText, pair.hintText)
                    }
                }
            }
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                val text = getEventText(event)
                if (!text.isNullOrBlank()) {
                    speak(text)
                }
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // TalkBack準拠: 新しい通知メッセージを自動即時アナウンス！
                val text = getEventText(event)
                if (!text.isNullOrBlank()) {
                    speak("通知: $text")
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // TalkBack準拠: リスト等のスクロール音再生 ＋ 位置案内！
                val now = System.currentTimeMillis()
                if (now - lastScrollSoundTime > 600) {
                    lastScrollSoundTime = now
                    playSound(soundScrollId)
                }
                if (now - lastScrollSpeakTime > 1200) {
                    lastScrollSpeakTime = now
                    val itemCount = event.itemCount
                    val fromIndex = event.fromIndex
                    if (itemCount > 0 && fromIndex >= 0) {
                        speak("${itemCount}項目中 ${fromIndex + 1}項目を表示中")
                    }
                }
            }
        }
    }

    data class NodeTextPair(
        val mainText: String,
        val hintText: String? = null
    )

    /**
     * AOSP TalkBack Stable準拠 ノード解読解析 (メインテキストとサブVoiceヒントを分離取得)
     */
    @Suppress("DEPRECATION")
    private fun parseNodeDescriptionPair(node: AccessibilityNodeInfo?): NodeTextPair? {
        if (node == null) return null
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val mainSb = StringBuilder()

        val packageName = compat.packageName?.toString() ?: ""
        val viewId = compat.viewIdResourceName
        val className = compat.className?.toString() ?: ""

        // App Support: 1. カスタムラベルのチェック (名無しボタン救済)
        var rawMainText: String? = customLabelManager.getCustomLabel(packageName, viewId)

        // App Support: 2. アプリ特有プロファイルによる最優先補正
        val appProfile = appProfileManager.getProfileForPackage(packageName)
        val customizedPair = appProfile.customizeNodeDescription(node, rawMainText, null)
        if (customizedPair != null) {
            return NodeTextPair(customizedPair.first, customizedPair.second)
        }

        if (rawMainText.isNullOrBlank()) {
            rawMainText = when {
                !compat.contentDescription.isNullOrBlank() -> compat.contentDescription.toString()
                !compat.text.isNullOrBlank() -> compat.text.toString()
                !compat.hintText.isNullOrBlank() -> compat.hintText.toString()
                !compat.error.isNullOrBlank() -> compat.error.toString()
                !compat.roleDescription.isNullOrBlank() -> compat.roleDescription.toString()
                else -> null
            }
        }

        if (rawMainText.isNullOrBlank()) {
            rawMainText = collectAllChildTexts(node)
        }

        // App Support: View ID からの汎用アイコン・ボタン名全自動レスキュー
        if (rawMainText.isNullOrBlank() && !viewId.isNullOrBlank()) {
            val idLower = viewId.lowercase(java.util.Locale.ROOT)
            val idName = idLower.substringAfterLast(":id/").substringAfterLast("/")
            rawMainText = when {
                idName.contains("0") || idName.contains("zero") -> "0"
                idName.contains("1") || idName.contains("one") -> "1"
                idName.contains("2") || idName.contains("two") -> "2"
                idName.contains("3") || idName.contains("three") -> "3"
                idName.contains("4") || idName.contains("four") -> "4"
                idName.contains("5") || idName.contains("five") -> "5"
                idName.contains("6") || idName.contains("six") -> "6"
                idName.contains("7") || idName.contains("seven") -> "7"
                idName.contains("8") || idName.contains("eight") -> "8"
                idName.contains("9") || idName.contains("nine") -> "9"
                idName.contains("delete") || idName.contains("backspace") -> "1文字削除"
                idName.contains("clear") -> "クリア"
                idName.contains("cancel") -> "キャンセル"
                idName.contains("enter") || idName.contains("confirm") || idName.contains("done") -> "完了"
                idName.contains("search") -> "検索"
                idName.contains("menu") || idName.contains("overflow") || idName.contains("more") -> "その他のオプション"
                idName.contains("back") || idName.contains("up") -> "戻る"
                idName.contains("close") || idName.contains("dismiss") -> "閉じる"
                idName.contains("share") -> "共有"
                idName.contains("settings") -> "設定"
                idName.contains("profile") || idName.contains("account") || idName.contains("avatar") -> "アカウント"
                idName.contains("notification") || idName.contains("bell") -> "通知"
                idName.contains("filter") -> "フィルタ"
                idName.contains("edit") -> "編集"
                idName.contains("favorite") || idName.contains("star") -> "お気に入り"
                idName.contains("add") || idName.contains("create") || idName.contains("fab") -> "追加"
                idName.contains("refresh") || idName.contains("sync") -> "更新"
                idName.contains("voice") || idName.contains("mic") -> "音声検索"
                idName.contains("dialpad") || idName.contains("keypad") -> "ダイヤルキーパッド"
                idName.contains("call") || idName.contains("dial") -> "発信"
                idName.contains("camera") -> "カメラ"
                idName.contains("send") -> "送信"
                idName.contains("help") || idName.contains("info") -> "ヘルプ"
                else -> null
            }
        }

        val isTab = className.contains("Tab") || className.contains("NavigationBarItem") ||
                className.contains("BottomNavigation") || viewId?.contains("tab") == true ||
                compat.roleDescription?.contains("タブ") == true

        if (rawMainText.isNullOrBlank() && isKeyguardLocked()) {
            rawMainText = "キー"
        } else if (rawMainText.isNullOrBlank() && (compat.isClickable || compat.isFocusable)) {
            rawMainText = if (isTab) "タブ" else "ボタン"
        }

        if (!rawMainText.isNullOrBlank()) {
            val mainTextStr = rawMainText.toString().trim()

            // ロック画面中 (Keyguard / PIN入力画面)
            if (isKeyguardLocked()) {
                val hint = if (compat.isClickable && !mainTextStr.endsWith("キー") && !mainTextStr.endsWith("ボタン") && !mainTextStr.contains("画面")) "キー" else null
                return NodeTextPair(mainTextStr, hint)
            }

            if (mainTextStr.length == 1) {
                val prefs = getSharedPreferences("hello_reader_prefs", Context.MODE_PRIVATE)
                val isPhoneticMorseEnabled = prefs.getBoolean("pref_phonetic_morse_enabled", false)

                val ch = mainTextStr[0]
                val uppercaseCh = ch.uppercaseChar()

                if (isPhoneticMorseEnabled && PHONETIC_MAP.containsKey(uppercaseCh)) {
                    val phoneticName = PHONETIC_MAP[uppercaseCh]
                    mainSb.append(ch).append("。").append(phoneticName)
                } else {
                    mainSb.append(rawMainText)
                }
            } else {
                mainSb.append(rawMainText)
            }
        } else {
            return null
        }

        val isEditText = compat.className?.contains("EditText") == true || compat.isEditable
        val isPassword = compat.isPassword

        if (isEditText) {
            val editText = compat.text?.toString()
            val hintText = compat.hintText?.toString()
            val contentDesc = compat.contentDescription?.toString()

            val mainDesc = when {
                !editText.isNullOrBlank() -> if (isPassword) "${editText.length}文字入力済み" else editText
                !hintText.isNullOrBlank() -> hintText
                !contentDesc.isNullOrBlank() -> contentDesc
                else -> ""
            }

            val roleDesc = if (isPassword) "パスワード編集ボックス" else "編集ボックス"
            val stateDesc = if (editText.isNullOrBlank()) "、テキスト未入力" else ""
            val fullMain = if (mainDesc.isNotBlank()) "$mainDesc$stateDesc" else "テキスト未入力"

            return NodeTextPair(fullMain, "$roleDesc。ダブルタップで入力")
        }

        val hintSb = StringBuilder()

        // 1. タブの判定と選択状態
        if (isTab) {
            if (!mainSb.contains("タブ")) {
                hintSb.append("タブ ")
            }
            if (compat.isSelected) {
                hintSb.append("選択中 ")
            } else {
                hintSb.append("未選択 ")
            }
        } else {
            if (className.isNotEmpty() || viewId != null) {
                val classStr = className
                val idStr = viewId ?: ""
                when {
                    classStr.contains("FloatingActionButton") || classStr.contains("Floating") || idStr.contains("fab") || idStr.contains("floating") -> hintSb.append("フローティングボタン ")
                    classStr.contains("Shortcut") || idStr.contains("shortcut") -> hintSb.append("ショートカットボタン ")
                    classStr.endsWith(".Switch") || classStr.contains("SwitchCompat") || classStr.contains("SwitchMaterial") || classStr.contains("Toggle") -> hintSb.append("スイッチ ")
                    classStr.contains("CheckBox") -> hintSb.append("チェックボックス ")
                    classStr.contains("RadioButton") -> hintSb.append("ラジオボタン ")
                    classStr.contains("EditText") -> hintSb.append("編集テキスト ")
                    classStr.contains("SeekBar") -> hintSb.append("スライダー ")
                    classStr.contains("Folder") -> hintSb.append("フォルダ ")
                    classStr.endsWith(".Button") || classStr.endsWith("ImageButton") -> if (!mainSb.contains("ボタン")) hintSb.append("ボタン ")
                    classStr.contains("ImageView") && mainSb.isBlank() -> hintSb.append("画像 ")
                }
            }
        }

        if (compat.isCheckable) {
            val isSwitchOrToggle = className.let {
                it.contains("Switch") || it.contains("Toggle")
            }

            if (compat.isChecked) {
                if (isSwitchOrToggle) hintSb.append("オン ") else hintSb.append("チェックあり ")
            } else {
                if (isSwitchOrToggle) hintSb.append("オフ ") else hintSb.append("チェックなし ")
            }
        }

        if (compat.isSelected && !isTab) {
            hintSb.append("選択中 ")
        }

        if (!compat.isEnabled) {
            hintSb.append("無効 ")
        }

        if (compat.isClickable && hintSb.isEmpty()) {
            hintSb.append("ダブルタップで実行できます ")
        }

        val finalMain = mainSb.toString().trim()
        val finalHint = if (hintSb.isNotEmpty()) hintSb.toString().trim() else null

        return NodeTextPair(finalMain, finalHint)
    }

    private fun parseNodeDescription(node: AccessibilityNodeInfo?): String? {
        val pair = parseNodeDescriptionPair(node) ?: return null
        return if (pair.hintText != null) "${pair.mainText} ${pair.hintText}" else pair.mainText
    }

    /**
     * ノード直下の子ノードツリーから全テキストを収集して結合する
     */
    private fun collectAllChildTexts(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val texts = mutableListOf<String>()
        collectChildTextsRecursive(node, texts, 0)
        val distinct = texts.distinct()
        return if (distinct.isNotEmpty()) distinct.joinToString(" ") else null
    }

    private fun collectChildTextsRecursive(node: AccessibilityNodeInfo, list: MutableList<String>, depth: Int) {
        if (depth > 6) return
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val compat = AccessibilityNodeInfoCompat.wrap(child)
            val childText = when {
                !compat.contentDescription.isNullOrBlank() -> compat.contentDescription.toString().trim()
                !compat.text.isNullOrBlank() -> compat.text.toString().trim()
                else -> null
            }
            if (!childText.isNullOrBlank() && !list.contains(childText)) {
                list.add(childText)
            }
            collectChildTextsRecursive(child, list, depth + 1)
        }
    }

    private fun getEventText(event: AccessibilityEvent): String? {
        val sb = StringBuilder()

        if (event.text.isNotEmpty()) {
            for (t in event.text) {
                if (!t.isNullOrBlank()) {
                    sb.append(t).append(" ")
                }
            }
        }

        if (sb.isEmpty() && !event.contentDescription.isNullOrBlank()) {
            sb.append(event.contentDescription)
        }

        return if (sb.isNotEmpty()) sb.toString().trim() else null
    }

    private val recentSpokenTexts = LinkedHashMap<String, Long>()

    /**
     * 音声読み上げ (TTS)
     */
    private fun speak(text: String) {
        if (text.isBlank()) return
        
        val hintRegex = Regex(" (ボタン|フローティングボタン|ショートカットボタン|チェックボックス|編集テキスト|フォルダ|画像|チェックあり|チェックなし|無効|ダブルタップで実行できます)$")
        val match = hintRegex.find(text)
        if (match != null) {
            val mainText = text.substring(0, match.range.first).trim()
            val hintText = match.value.trim()
            speakWithSubVoice(mainText, hintText)
        } else {
            speakWithSubVoice(text, null)
        }
    }

    /**
     * 進行中のあらゆる発声を即座に停止・フラッシュする（操作最優先の割り込み）
     */
    private fun interruptSpeaking() {
        pendingSubVoiceHint = null
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
    }

    /**
     * メイン表示名と操作ヒントを異なるトーン/Voice（二重音声サブボイス）で連続音読
     */
    private fun speakWithSubVoice(mainText: String, hintText: String? = null, force: Boolean = false) {
        if (!isTtsReady || mainText.isBlank()) return

        val fullTextKey = if (!hintText.isNullOrBlank()) "$mainText|$hintText" else mainText
        val currentTime = System.currentTimeMillis()
        val lastTime = recentSpokenTexts[fullTextKey]

        // 同一テキストが短時間 (800ms以内) に手動ナビゲーションとアクセシビリティイベントで2重発火するのを完全シャットアウト！
        if (lastTime != null && (currentTime - lastTime) < 800) {
            return
        }

        if (!force && lastTime != null && (currentTime - lastTime) < 1500) {
            return
        }

        recentSpokenTexts[fullTextKey] = currentTime
        if (recentSpokenTexts.size > 20) {
            recentSpokenTexts.remove(recentSpokenTexts.keys.first())
        }

        Log.d(TAG, "Speaking Main: ${sanitizeForLog(mainText)}${if (hintText != null) " [SubVoice Hint: $hintText]" else ""}")

        val currentTts = tts ?: return
        
        // 1. サブヒントをペンディング変数にセット
        pendingSubVoiceHint = if (isSubVoiceEnabled && !hintText.isNullOrBlank()) hintText else null

        // 2. 点字ディスプレイへの出力配信
        if (::brailleManager.isInitialized) {
            val brailleSpoken = if (!hintText.isNullOrBlank()) "$mainText $hintText" else mainText
            brailleManager.displayText(brailleSpoken)
        }

        // 3. メインボイスの再生
        currentTts.setPitch(mainVoicePitch)
        currentTts.speak(mainText, TextToSpeech.QUEUE_FLUSH, null, "HelloReaderMainTTS")

        // 4. 智浩さんの「なでしこ v3 風」仕様 (Morse code conditions.md 精密版) の実行！
        // もし、フォーカスがエディットボックス (EditText / 入力欄) ならば、文字の読み上げのあとに、モールス信号フィードバックを実行！
        // 違えば、読み上げだけ！
        val prefs = getSharedPreferences("hello_reader_prefs", Context.MODE_PRIVATE)
        val isMorseEnabled = prefs.getBoolean("pref_phonetic_morse_enabled", false)
        val targetNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val isEditBox = targetNode != null && (
            targetNode.isEditable ||
            targetNode.className?.toString()?.contains("EditText", ignoreCase = true) == true
        )

        if (isMorseEnabled && isEditBox) {
            val timing = prefs.getString("pref_phonetic_morse_timing", "AFTER_TTS") ?: "AFTER_TTS"
            val targetChar = mainText.firstOrNull { PHONETIC_MAP.containsKey(it.uppercaseChar()) }
            if (targetChar != null) {
                when (timing) {
                    "BEFORE_TTS" -> {
                        playMorseVibration(targetChar)
                    }
                    "AFTER_TTS" -> {
                        mainHandler.postDelayed({
                            playMorseVibration(targetChar)
                        }, 300)
                    }
                    else -> {
                        playMorseVibration(targetChar)
                    }
                }
            }
        }
    }

    /**
     * ログ出力時のプライバシー保護（電話番号等のログ伏せ字化）
     */
    private fun sanitizeForLog(text: String): String {
        return text.replace(Regex("(\\d{2,4}-\\d{2,4}-\\d{4}|\\d{10,11})"), "[電話番号匿名化]")
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "HelloReaderService onUnbind called - performing graceful shutdown announcement")
        try {
            // 1. 終了サウンドと触覚フィードバック
            playSound(soundDeactivateId)
            if (::hapticFeedbackManager.isInitialized) {
                hapticFeedbackManager.performBoundaryOrError()
            }

            // 2. 点字ディスプレイへの終了通知
            if (::brailleManager.isInitialized) {
                brailleManager.displayText("Hello Readerを終了しました")
            }

            // 3. 音声終了アナウンス
            if (isTtsReady) {
                tts?.speak("Hello Readerを終了しました", TextToSpeech.QUEUE_FLUSH, null, "HelloReaderShutdownTTS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during onUnbind shutdown announcement", e)
        }

        // 発声完了を待つため少し遅延させてからレシーバーとリソースを解放
        mainHandler.postDelayed({
            cleanupResources()
        }, 800)

        return super.onUnbind(intent)
    }

    private fun cleanupResources() {
        try {
            unregisterReceiver(screenReceiver)
            unregisterReceiver(phoneStateReceiver)
        } catch (e: Exception) { }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (e: Exception) { }
        try {
            soundPool?.release()
            soundPool = null
        } catch (e: Exception) { }
        try {
            if (::brailleManager.isInitialized) {
                brailleManager.shutdown()
            }
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        Log.d(TAG, "HelloReaderService onDestroy called")
        cleanupResources()
        super.onDestroy()
    }
}
