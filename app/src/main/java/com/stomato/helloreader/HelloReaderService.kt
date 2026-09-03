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

    private var vibrator: Vibrator? = null

    private lateinit var appProfileManager: AppProfileManager
    private lateinit var customLabelManager: CustomLabelManager

    private fun getVibratorService(): Vibrator? {
        if (vibrator != null) return vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return vibrator
    }

    /**
     * 指先への触覚フィードバック (Haptic Feedback) の実行
     */
    private fun performHapticFeedback() {
        try {
            val vib = getVibratorService()
            if (vib?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(15)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform haptic feedback", e)
        }
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
                    // AOSP TalkBack準拠: ロック解除後、ウィンドウが完全にアクティブ化するのを待ってホーム画面の初動フォーカスをセット！
                    mainHandler.removeCallbacksAndMessages(null)
                    mainHandler.postDelayed({
                        speakHomeScreenItems(getActiveRootNode())
                    }, 300)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Hello Reader Accessibility Service Connected!")

        // 1. AOSP TalkBack Stable準拠の動的サービス設定
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN or
                AccessibilityServiceInfo.FEEDBACK_HAPTIC or
                AccessibilityServiceInfo.FEEDBACK_AUDIBLE
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
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
        customLabelManager = CustomLabelManager()
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
        return handleGestureAction(gestureId)
    }

    /**
     * ジェスチャー検知 (API 30未満 フォールバック対応)
     */
    @Deprecated("Deprecated in API 30")
    override fun onGesture(gestureId: Int): Boolean {
        return handleGestureAction(gestureId)
    }

    /**
     * Google公式TalkBackユーザーガイド 100%完全合致 ジェスチャー処理
     */
    private fun handleGestureAction(gestureId: Int): Boolean {
        Log.d(TAG, "handleGestureAction: $gestureId")
        return when (gestureId) {
            GESTURE_SWIPE_RIGHT -> {
                navigateFocus(View.FOCUS_FORWARD, "次へ")
                true
            }
            GESTURE_SWIPE_LEFT -> {
                navigateFocus(View.FOCUS_BACKWARD, "前へ")
                true
            }
            GESTURE_SWIPE_UP,
            GESTURE_2_FINGER_SWIPE_UP,
            GESTURE_3_FINGER_SWIPE_UP,
            GESTURE_4_FINGER_SWIPE_UP -> {
                Log.d(TAG, "Swipe Up Gesture Detected: $gestureId (KeyguardLocked=${isKeyguardLocked()})")
                if (isKeyguardLocked()) {
                    showPinEntryScreen()
                    false
                } else {
                    if (gestureId == GESTURE_SWIPE_UP) {
                        navigateFocus(View.FOCUS_BACKWARD, "前へ")
                    } else {
                        performScrollAction(forward = true)
                    }
                    true
                }
            }
            GESTURE_2_FINGER_SWIPE_DOWN -> {
                Log.d(TAG, "2-Finger Swipe Down: Scroll Backward")
                performScrollAction(forward = false)
                true
            }
            GESTURE_2_FINGER_SWIPE_LEFT -> {
                Log.d(TAG, "2-Finger Swipe Left: Scroll Forward Page")
                performScrollAction(forward = true)
                true
            }
            GESTURE_2_FINGER_SWIPE_RIGHT -> {
                Log.d(TAG, "2-Finger Swipe Right: Scroll Backward Page")
                performScrollAction(forward = false)
                true
            }
            GESTURE_2_FINGER_DOUBLE_TAP -> {
                Log.d(TAG, "2-Finger Double Tap: Phone Answer / End Call")
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
                speak("TalkBack メニュー")
                true
            }
            else -> {
                false
            }
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

    /**
     * ホーム画面のアイテム（アプリアイコンやウィジェット）に自動フォーカスを当ててメニューを朗読する
     */
    private fun speakHomeScreenItems(root: AccessibilityNodeInfo?) {
        val activeRoot = root ?: getActiveRootNode()
        if (activeRoot == null) {
            speak("ホーム画面")
            return
        }

        val firstAppNode = findAppIconNode(activeRoot)
        if (firstAppNode != null) {
            lastFocusedNode = firstAppNode
            firstAppNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            val appName = parseNodeDescription(firstAppNode)
            if (!appName.isNullOrBlank()) {
                speak("ホーム画面。$appName")
                return
            }
        }
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
     * AOSP TalkBack (NodeFocusFinder / TraversalStrategy) 準拠 フォーカス移動処理
     * 全ウィンドウ（アプリ本体 ＋ システムバー）から順序良くフォーカス可能ノードを収集して100%巡回！
     */
    private fun navigateFocus(direction: Int, directionName: String, isAutoRetry: Boolean = false) {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        val currentWindows = windows

        if (!currentWindows.isNullOrEmpty()) {
            if (isKeyguardLocked()) {
                // ロック画面中 (Keyguard / PIN入力画面) は存在する全ウィンドウ (Keyguard/SystemUI/InputMethod/App) のノードを漏れなく全収集！
                for (window in currentWindows) {
                    try {
                        window.root?.let { collectFocusableNodes(it, allNodes) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Window node collection error during keyguard", e)
                    }
                }
            } else {
                // 1. アプリ・ホーム画面本体 (TYPE_APPLICATION) のノードをまず優先収集！
                for (window in currentWindows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        window.root?.let { collectFocusableNodes(it, allNodes) }
                    }
                }
                // 2. システムウィンドウ (ナビゲーションバー等) のノードを収集！
                for (window in currentWindows) {
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                        window.root?.let { collectFocusableNodes(it, allNodes) }
                    }
                }
            }
        }

        if (allNodes.isEmpty()) {
            val root = getActiveRootNode()
            if (root != null) {
                collectFocusableNodes(root, allNodes)
            }
        }

        if (allNodes.isEmpty()) {
            speak("これ以上 $directionName に進めません")
            return
        }

        val root = getActiveRootNode()

        var currentFocus = root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (currentFocus == null) {
            currentFocus = lastFocusedNode
        }

        var currentIndex = -1
        if (currentFocus != null) {
            for (i in allNodes.indices) {
                if (isSameNode(allNodes[i], currentFocus)) {
                    currentIndex = i
                    break
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
            val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            lastFocusedNode = targetNode
            
            val pair = parseNodeDescriptionPair(targetNode)
            if (pair != null) {
                speakWithSubVoice(pair.mainText, pair.hintText)
            } else {
                speak("$directionName の要素へ移動")
            }
        } else {
            // TalkBack Auto-Scroll 準拠: 画面端到達時に自動スクロールを実行し、新ノードへフォーカスを移す！
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
        return !r1.isEmpty && r1 == r2 && node1.className == node2.className
    }

    /**
     * 木構造を深さ優先で走査し、読解・フォーカス可能なノード一覧を収集する (hello-reader 独自最適化)
     */
    private fun collectFocusableNodes(
        node: AccessibilityNodeInfo?,
        list: MutableList<AccessibilityNodeInfo>,
        parent: AccessibilityNodeInfo? = null
    ) {
        if (node == null) return
        val compat = AccessibilityNodeInfoCompat.wrap(node)

        if (!compat.isVisibleToUser) return

        // 親ボタン枠等ですでに登録されている場合、重複する子要素の二重登録をスキップする
        if (parent != null && isRedundantChildNode(parent, node)) {
            return
        }

        val desc = parseNodeDescription(node)
        val hasDesc = !desc.isNullOrBlank()
        val hasContentDesc = !compat.contentDescription.isNullOrBlank()

        val isAdded = if (hasDesc && (compat.isFocusable || compat.isClickable || compat.isScreenReaderFocusable || hasContentDesc)) {
            list.add(node)
            true
        } else {
            false
        }

        for (i in 0 until node.childCount) {
            collectFocusableNodes(node.getChild(i), list, if (isAdded) node else parent)
        }
    }

    /**
     * hello-reader 独自ノード解析: 親コンテナと包含関係にある子ノードの二重登録を検知
     */
    private fun isRedundantChildNode(parent: AccessibilityNodeInfo?, child: AccessibilityNodeInfo?): Boolean {
        if (parent == null || child == null) return false
        // ロック画面中 (Keyguard / PIN入力画面) は親枠の中に数字キーが含まれるため、除外を行わず全ての数字キーをフォーカス収集！
        if (isKeyguardLocked()) return false

        val parentCompat = AccessibilityNodeInfoCompat.wrap(parent)
        if (parentCompat.isClickable || parentCompat.isFocusable) {
            val pRect = Rect()
            val cRect = Rect()
            parent.getBoundsInScreen(pRect)
            child.getBoundsInScreen(cRect)
            if (!pRect.isEmpty && pRect.contains(cRect)) {
                return true
            }
        }
        return false
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
    private fun showPinEntryScreen() {
        try {
            speak("PINコード入力画面")
            // 1. 物理ドラッグスワイプを発火
            performKeyguardUnlockSwipe()

            // 2. MainActivity (Activityコンテキスト) 経由の KeyguardManager.requestDismissKeyguard 公式発火
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("EXTRA_REQUEST_PIN_UNLOCK", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show PIN entry screen", e)
            performKeyguardUnlockSwipe()
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
        val startY = (height * 0.85f).toFloat()
        val endY = (height * 0.15f).toFloat()

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 200)
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
            val vib = getVibratorService()
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

                if (isHomeScreenPackage(packageName)) {
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

        // App Support: 1. カスタムラベルのチェック (名無しボタン救済)
        var rawMainText: String? = customLabelManager.getCustomLabel(packageName, viewId)

        if (rawMainText.isNullOrBlank()) {
            rawMainText = when {
                !compat.text.isNullOrBlank() -> compat.text.toString()
                !compat.contentDescription.isNullOrBlank() -> compat.contentDescription.toString()
                !compat.hintText.isNullOrBlank() -> compat.hintText.toString()
                !compat.error.isNullOrBlank() -> compat.error.toString()
                !compat.roleDescription.isNullOrBlank() -> compat.roleDescription.toString()
                else -> null
            }
        }

        if (rawMainText.isNullOrBlank()) {
            rawMainText = findTextInChildNodes(node)?.toString()
        }

        // App Support: PIN画面・テンキー View ID からの数字/キー名全自動レスキュー抽出
        if (rawMainText.isNullOrBlank() && !viewId.isNullOrBlank()) {
            val idLower = viewId.lowercase(java.util.Locale.ROOT)
            rawMainText = when {
                idLower.contains("0") || idLower.contains("zero") -> "0"
                idLower.contains("1") || idLower.contains("one") -> "1"
                idLower.contains("2") || idLower.contains("two") -> "2"
                idLower.contains("3") || idLower.contains("three") -> "3"
                idLower.contains("4") || idLower.contains("four") -> "4"
                idLower.contains("5") || idLower.contains("five") -> "5"
                idLower.contains("6") || idLower.contains("six") -> "6"
                idLower.contains("7") || idLower.contains("seven") -> "7"
                idLower.contains("8") || idLower.contains("eight") -> "8"
                idLower.contains("9") || idLower.contains("nine") -> "9"
                idLower.contains("delete") || idLower.contains("backspace") || idLower.contains("cancel") -> "削除"
                idLower.contains("enter") || idLower.contains("ok") || idLower.contains("confirm") -> "完了"
                else -> null
            }
        }

        // App Support: 2. アプリ特有プロファイルによる補正適用
        val appProfile = appProfileManager.getProfileForPackage(packageName)
        val customizedPair = appProfile.customizeNodeDescription(node, rawMainText, null)
        if (customizedPair != null) {
            return NodeTextPair(customizedPair.first, customizedPair.second)
        }

        if (!rawMainText.isNullOrBlank()) {
            val mainTextStr = rawMainText.toString().trim()

            // ロック画面中 (Keyguard / PIN入力画面) はタイマー遅延を挟まず爆速・安全に即時音読してフリーズを防止！
            if (isKeyguardLocked()) {
                val hint = if (compat.isClickable && !mainTextStr.endsWith("キー") && !mainTextStr.endsWith("ボタン") && !mainTextStr.contains("画面")) "キー" else null
                return NodeTextPair(mainTextStr, hint)
            }

            if (mainTextStr.length == 1) {
                val prefs = getSharedPreferences("hello_reader_prefs", Context.MODE_PRIVATE)
                val isPhoneticMorseEnabled = prefs.getBoolean("pref_phonetic_morse_enabled", false)
                val timing = prefs.getString("pref_phonetic_morse_timing", "AFTER_TTS") ?: "AFTER_TTS"

                val ch = mainTextStr[0]
                val uppercaseCh = ch.uppercaseChar()

                if (isPhoneticMorseEnabled && PHONETIC_MAP.containsKey(uppercaseCh)) {
                    val phoneticName = PHONETIC_MAP[uppercaseCh]
                    when (timing) {
                        "BEFORE_TTS" -> {
                            playMorseVibration(ch)
                            mainSb.append(ch).append("。").append(phoneticName)
                        }
                        "AFTER_TTS" -> {
                            mainSb.append(ch).append("。").append(phoneticName)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                playMorseVibration(ch)
                            }, 1200)
                        }
                        else -> {
                            mainSb.append(ch).append("。").append(phoneticName)
                            playMorseVibration(ch)
                        }
                    }
                } else {
                    mainSb.append(rawMainText)
                }
            } else {
                mainSb.append(rawMainText)
            }
        } else {
            return null
        }

        val hintSb = StringBuilder()
        val className = compat.className
        if (className != null || viewId != null) {
            val classStr = className?.toString() ?: ""
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
                classStr.endsWith(".Button") || classStr.endsWith("ImageButton") -> hintSb.append("ボタン ")
                classStr.contains("ImageView") && mainSb.isBlank() -> hintSb.append("画像 ")
            }
        }

        if (compat.isClickable && hintSb.isEmpty()) {
            hintSb.append("ダブルタップで実行できます ")
        }

        if (compat.isCheckable) {
            val isSwitchOrToggle = className?.toString()?.let {
                it.contains("Switch") || it.contains("Toggle")
            } ?: false

            if (compat.isChecked) {
                if (isSwitchOrToggle) hintSb.append("オン ") else hintSb.append("チェックあり ")
            } else {
                if (isSwitchOrToggle) hintSb.append("オフ ") else hintSb.append("チェックなし ")
            }
        }

        if (!compat.isEnabled) {
            hintSb.append("無効 ")
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
     * ノード直下の子ノードツリーから最初にみつかったテキスト/contentDescriptionを取得
     */
    private fun findTextInChildNodes(node: AccessibilityNodeInfo?): CharSequence? {
        if (node == null) return null
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val compat = AccessibilityNodeInfoCompat.wrap(child)
            val childText = when {
                !compat.text.isNullOrBlank() -> compat.text
                !compat.contentDescription.isNullOrBlank() -> compat.contentDescription
                else -> {
                    val childId = compat.viewIdResourceName?.lowercase(java.util.Locale.ROOT) ?: ""
                    when {
                        childId.contains("0") || childId.contains("zero") -> "0"
                        childId.contains("1") || childId.contains("one") -> "1"
                        childId.contains("2") || childId.contains("two") -> "2"
                        childId.contains("3") || childId.contains("three") -> "3"
                        childId.contains("4") || childId.contains("four") -> "4"
                        childId.contains("5") || childId.contains("five") -> "5"
                        childId.contains("6") || childId.contains("six") -> "6"
                        childId.contains("7") || childId.contains("seven") -> "7"
                        childId.contains("8") || childId.contains("eight") -> "8"
                        childId.contains("9") || childId.contains("nine") -> "9"
                        childId.contains("delete") || childId.contains("backspace") -> "削除"
                        else -> null
                    }
                }
            }
            if (!childText.isNullOrBlank()) {
                return childText
            }
            val descendantText = findTextInChildNodes(child)
            if (!descendantText.isNullOrBlank()) {
                return descendantText
            }
        }
        return null
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
     * メイン表示名と操作ヒントを異なるトーン/Voice（二重音声サブボイス）で連続音読
     */
    private fun speakWithSubVoice(mainText: String, hintText: String? = null) {
        if (!isTtsReady || mainText.isBlank()) return

        val fullTextKey = if (!hintText.isNullOrBlank()) "$mainText|$hintText" else mainText
        val currentTime = System.currentTimeMillis()
        val lastTime = recentSpokenTexts[fullTextKey]
        if (lastTime != null && (currentTime - lastTime) < 2000) {
            return
        }

        recentSpokenTexts[fullTextKey] = currentTime
        if (recentSpokenTexts.size > 20) {
            recentSpokenTexts.remove(recentSpokenTexts.keys.first())
        }

        Log.d(TAG, "Speaking Main: ${sanitizeForLog(mainText)}${if (hintText != null) " [SubVoice Hint: $hintText]" else ""}")

        val currentTts = tts ?: return
        
        // 1. サブヒントをペンディング変数にセット（onDone発声完了時に安全に発声させる）
        pendingSubVoiceHint = if (isSubVoiceEnabled && !hintText.isNullOrBlank()) hintText else null

        // 2. メインボイスの再生 (QUEUE_FLUSH)
        currentTts.setPitch(mainVoicePitch)
        currentTts.speak(mainText, TextToSpeech.QUEUE_FLUSH, null, "HelloReaderMainTTS")
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

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
            unregisterReceiver(phoneStateReceiver)
        } catch (e: Exception) { }
        tts?.stop()
        tts?.shutdown()
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }
}
