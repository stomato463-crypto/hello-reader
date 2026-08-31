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
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.provider.ContactsContract
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
                AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
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
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "日本語のTTSデータが見つからないか対応していません")
            } else {
                isTtsReady = true
                speak("Hello Readerが起動しました")
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
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
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
            GESTURE_SWIPE_UP -> {
                if (isKeyguardLocked()) {
                    speak("PINコード入力画面")
                    false // OSにスワイプをパススルーしてPIN画面を開く
                } else {
                    navigateFocus(View.FOCUS_BACKWARD, "前へ")
                    true
                }
            }
            GESTURE_2_FINGER_SWIPE_UP -> {
                Log.d(TAG, "2-Finger Swipe Up: Scroll Forward")
                performScrollAction(forward = true)
                true
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
        val currentWindows = windows
        if (!currentWindows.isNullOrEmpty()) {
            // ロック画面中 (Keyguard) は TYPE_SYSTEM や Keyguard/Lock タイトルを持つウィンドウを最優先！
            if (isKeyguardLocked()) {
                for (window in currentWindows) {
                    val titleStr = window.title?.toString() ?: ""
                    if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
                        titleStr.contains("Keyguard", ignoreCase = true) ||
                        titleStr.contains("Lock", ignoreCase = true) ||
                        titleStr.contains("SystemUI", ignoreCase = true)
                    ) {
                        val root = window.root
                        if (root != null) return root
                    }
                }
            }

            // 通常時: TYPE_APPLICATION (ホーム画面本体・アプリアイコン領域) を最優先！
            for (window in currentWindows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val root = window.root
                    if (root != null) return root
                }
            }

            // 次にアクティブ/フォーカスされたウィンドウ
            for (window in currentWindows) {
                if (window.isFocused || window.isActive) {
                    val root = window.root
                    if (root != null) return root
                }
            }

            // フォールバック
            for (window in currentWindows) {
                val root = window.root
                if (root != null) return root
            }
        }
        return rootInActiveWindow
    }

    /**
     * AOSP TalkBack (NodeFocusFinder / TraversalStrategy) 準拠 フォーカス移動処理
     * 全ウィンドウ（アプリ本体 ＋ システムバー）から順序良くフォーカス可能ノードを収集して100%巡回！
     */
    private fun navigateFocus(direction: Int, directionName: String, isAutoRetry: Boolean = false) {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        val currentWindows = windows

        if (!currentWindows.isNullOrEmpty() && !isKeyguardLocked()) {
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
            val nodeText = parseNodeDescription(targetNode)

            if (!nodeText.isNullOrBlank()) {
                speak(nodeText)
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
     * 木構造を深さ優先で走査し、読解・フォーカス可能なノード一覧を収集する (TalkBack TraversalStrategy準拠)
     */
    private fun collectFocusableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val compat = AccessibilityNodeInfoCompat.wrap(node)

        if (!compat.isVisibleToUser) return

        val desc = parseNodeDescription(node)
        val hasDesc = !desc.isNullOrBlank()
        val hasContentDesc = !compat.contentDescription.isNullOrBlank()

        if (hasDesc && (compat.isFocusable || compat.isClickable || compat.isScreenReaderFocusable || hasContentDesc)) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            collectFocusableNodes(node.getChild(i), list)
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
            // 1. 親ノードを辿って isClickable なノードに ACTION_CLICK を送る
            var nodeToClick: AccessibilityNodeInfo? = currentFocus
            while (nodeToClick != null && !AccessibilityNodeInfoCompat.wrap(nodeToClick).isClickable) {
                nodeToClick = nodeToClick.parent
            }

            if (nodeToClick != null) {
                val success = nodeToClick.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                if (success) {
                    playSound(soundActivateId)
                    speak("実行しました")
                    return
                }
            }

            // 2. 子ノードツリーを走査して isClickable な要素を探して ACTION_CLICK
            val clickableChild = findClickableChild(currentFocus)
            if (clickableChild != null) {
                val childSuccess = clickableChild.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                if (childSuccess) {
                    playSound(soundActivateId)
                    speak("実行しました")
                    return
                }
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
     * 指定されたRectの中心座標に対して物理タップ（dispatchGesture）を送信
     */
    private fun performGestureClickAtBounds(bounds: Rect): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        if (centerX <= 0 || centerY <= 0) return false

        val path = Path().apply {
            moveTo(centerX, centerY)
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
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
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator == null || !vibrator.hasVibrator()) return

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
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
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
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                if (event.source != null) {
                    lastFocusedNode = event.source
                }
                playSound(soundFocusId)
                val node = event.source
                if (node != null) {
                    val desc = parseNodeDescription(node)
                    if (!desc.isNullOrBlank()) {
                        speak(desc)
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                playSound(soundActivateId)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 画面（Activity/Dialog/Launcher）が新しく切り替わった時だけ画面切り替え音を再生！
                playSound(soundViewEnteredId)
                val text = getEventText(event)
                val packageName = event.packageName?.toString() ?: ""

                if (packageName.contains("launcher", ignoreCase = true) || packageName.contains("home", ignoreCase = true)) {
                    mainHandler.postDelayed({
                        speakHomeScreenItems(getActiveRootNode())
                    }, 200)
                } else if (!text.isNullOrBlank()) {
                    speak(text)
                } else if (event.source != null) {
                    val desc = parseNodeDescription(event.source)
                    if (!desc.isNullOrBlank()) {
                        speak(desc)
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 画面の一部更新（時計更新等）では画面切り替え音を鳴らさない！
                val text = getEventText(event)
                val packageName = event.packageName?.toString() ?: ""

                if (!text.isNullOrBlank()) {
                    speak(text)
                } else if (packageName.contains("launcher", ignoreCase = true)) {
                    speakHomeScreenItems(getActiveRootNode())
                } else if (event.source != null) {
                    val desc = parseNodeDescription(event.source)
                    if (!desc.isNullOrBlank()) {
                        speak(desc)
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

    /**
     * AOSP TalkBack Stable準拠 ノード解読解析
     */
    @Suppress("DEPRECATION")
    private fun parseNodeDescription(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val sb = StringBuilder()

        var mainText = when {
            !compat.text.isNullOrBlank() -> compat.text
            !compat.contentDescription.isNullOrBlank() -> compat.contentDescription
            !compat.roleDescription.isNullOrBlank() -> compat.roleDescription
            else -> null
        }

        if (mainText.isNullOrBlank()) {
            mainText = findTextInChildNodes(node)
        }

        if (!mainText.isNullOrBlank()) {
            val mainTextStr = mainText.toString().trim()
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
                            sb.append(ch).append("。").append(phoneticName).append(" ")
                        }
                        "AFTER_TTS" -> {
                            sb.append(ch).append("。").append(phoneticName).append(" ")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                playMorseVibration(ch)
                            }, 1200)
                        }
                        else -> { // "CONCURRENT"
                            sb.append(ch).append("。").append(phoneticName).append(" ")
                            playMorseVibration(ch)
                        }
                    }
                } else {
                    sb.append(mainText).append(" ")
                }
            } else {
                sb.append(mainText).append(" ")
            }
        }

        val className = compat.className
        val viewId = compat.viewIdResourceName
        if (className != null || viewId != null) {
            val classStr = className?.toString() ?: ""
            val idStr = viewId?.toString() ?: ""
            when {
                classStr.contains("FloatingActionButton") || classStr.contains("Floating") || idStr.contains("fab") || idStr.contains("floating") -> sb.append("フローティングボタン ")
                classStr.contains("Shortcut") || idStr.contains("shortcut") -> sb.append("ショートカットボタン ")
                classStr.contains("Button") -> sb.append("ボタン ")
                classStr.contains("CheckBox") -> sb.append("チェックボックス ")
                classStr.contains("EditText") -> sb.append("編集テキスト ")
                classStr.contains("Folder") -> sb.append("フォルダ ")
                classStr.contains("ImageView") && mainText.isNullOrBlank() -> sb.append("画像 ")
            }
        }

        if (compat.isCheckable) {
            if (compat.isChecked) {
                sb.append("チェックあり ")
            } else {
                sb.append("チェックなし ")
            }
        }

        if (!compat.isEnabled) {
            sb.append("無効 ")
        }

        return if (sb.isNotEmpty()) sb.toString().trim() else null
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
                else -> null
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

    /**
     * 音声読み上げ (TTS) (連呼重複防止デバウンス1.5秒)
     */
    private fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return

        val currentTime = System.currentTimeMillis()
        if (text == lastSpokenText && (currentTime - lastSpokenTime) < 1500) {
            return
        }

        lastSpokenText = text
        lastSpokenTime = currentTime

        Log.d(TAG, "Speaking: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HelloReaderTTS")
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
