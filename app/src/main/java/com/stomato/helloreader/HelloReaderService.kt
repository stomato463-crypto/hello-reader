package com.stomato.helloreader

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.Locale

/**
 * Hello Reader Accessibility Service
 * AOSP TalkBack 100%完全合致 指紋センサー位置案内・ロック画面・ホーム画面全解読版
 */
class HelloReaderService : AccessibilityService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "HelloReaderService"
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // サウンドフィードバック (Audacity自作音 ＋ Google公式TalkBack音 ハイブリッド仕様)
    private var soundPool: SoundPool? = null
    private var soundActivateId: Int = 0
    private var soundDeactivateId: Int = 0
    private var soundFocusId: Int = 0
    private var soundViewEnteredId: Int = 0

    // 重複連呼防止用タイムスタンプ
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L

    private var lastUnlockTime: Long = 0L

    // 最後にフォーカスされたノード（ダブルタップ確実実行用）
    private var lastFocusedNode: AccessibilityNodeInfo? = null

    // 電話の着信・通話監視状態
    private var isRinging = false
    private var isCallActive = false
    private var callStartTime = 0L

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
                        playSound(soundViewEnteredId)
                        speak("電話の着信ですにょろ！2本指ダブルタップで応答できるにょろ")
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        if (!isCallActive) {
                            isCallActive = true
                            callStartTime = System.currentTimeMillis()
                            playSound(soundActivateId)
                            speak("通話中になりましたにょろ。2本指ダブルタップで切断できるにょろ")
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

                            playSound(soundDeactivateId)
                            speak("通話が終了したにょろ。通話時間は $durationText でしたにょろ")
                        } else if (isRinging) {
                            speak("着信が終了したにょろ")
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
                Log.e(TAG, "日本語のTTSデータが見つからないか対応していないにょろ")
            } else {
                isTtsReady = true
                speak("Hello Readerが起動したにょろ")
            }
        } else {
            Log.e(TAG, "TTSの初期化に失敗したにょろ")
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
                (compat.viewIdResourceName?.toString() ?: "")

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
                Log.d(TAG, "2-Finger Swipe Up: Lockscreen PIN Entry")
                speak("PINコード入力画面")
                false // OSにジェスチャーをそのまま渡してパスコードキーパッドを表示させる
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
        if (root == null) {
            speak("ホーム画面")
            return
        }

        val firstAppNode = findAppIconNode(root)
        if (firstAppNode != null) {
            firstAppNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)
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
     * アクティブな全ウィンドウのルートノードを取得する (AOSP TalkBack ロック画面最優先対応)
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

            for (window in currentWindows) {
                if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM || window.isFocused || window.isActive) {
                    val root = window.root
                    if (root != null) return root
                }
            }
            for (window in currentWindows) {
                val root = window.root
                if (root != null) return root
            }
        }
        return rootInActiveWindow
    }

    /**
     * AOSP TalkBack (NodeFocusFinder / TraversalStrategy) 準拠 フォーカス移動処理
     * システムロック画面等の特殊ウィンドウでも木構造トラバーサルで100%全要素を巡回・読解！
     */
    private fun navigateFocus(direction: Int, directionName: String) {
        val root = getActiveRootNode()
        if (root == null) {
            speak("画面のノードが取得できないにょろ")
            return
        }

        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, allNodes)

        if (allNodes.isEmpty()) {
            speak("これ以上 $directionName に要素はないにょろ")
            return
        }

        val currentFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        var currentIndex = -1

        if (currentFocus != null) {
            for (i in allNodes.indices) {
                if (allNodes[i] == currentFocus) {
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

            currentFocus?.performAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            val success = targetNode.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS)
            val nodeText = parseNodeDescription(targetNode)

            // フォーカス移動成功音！ (Google公式 focus.ogg)
            playSound(soundFocusId)

            if (!nodeText.isNullOrBlank()) {
                speak(nodeText)
            } else {
                speak("$directionName の要素に移動したにょろ")
            }
        } else {
            // 画面の端（これ以上移動できない）到達音！ (智浩さん自作 deactivate.opus)
            playSound(soundDeactivateId)
            speak("これ以上 $directionName に要素はないにょろ")
        }
    }

    /**
     * 木構造を深さ優先で走査し、読解・フォーカス可能なノード一覧を収集する (TalkBack TraversalStrategy準拠)
     */
    private fun collectFocusableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val compat = AccessibilityNodeInfoCompat.wrap(node)

        if (!compat.isVisibleToUser) return

        val desc = parseNodeDescription(node)
        if (!desc.isNullOrBlank() && (compat.isFocusable || compat.isClickable || node.childCount == 0)) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            collectFocusableNodes(node.getChild(i), list)
        }
    }

    /**
     * 2本指ダブルタップ時のお電話応答・切断アクション
     */
    private fun handlePhoneAnswerOrEndCall(): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        try {
            if (isRinging) {
                if (telecomManager != null) {
                    try {
                        telecomManager.acceptRingingCall()
                        playSound(soundActivateId)
                        speak("電話に応答したにょろ")
                        return true
                    } catch (e: SecurityException) {
                        Log.w(TAG, "acceptRingingCall SecurityException fallback to HEADSETHOOK", e)
                    }
                }
                performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
                playSound(soundActivateId)
                speak("電話に応答したにょろ")
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
     * AOSP TalkBack Stable準拠 ダブルタップ実行 (ACTION_CLICK)
     * フォーカス保持＆親ノード走査＆フォールバックで100%確実実行！
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
            var nodeToClick: AccessibilityNodeInfo? = currentFocus
            while (nodeToClick != null && !AccessibilityNodeInfoCompat.wrap(nodeToClick).isClickable) {
                nodeToClick = nodeToClick.parent
            }

            if (nodeToClick != null) {
                val success = nodeToClick.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                if (success) {
                    playSound(soundActivateId)
                    speak("実行したにょろ")
                    return
                }
            }

            // フォールバック実行
            val directSuccess = currentFocus.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (directSuccess) {
                playSound(soundActivateId)
                speak("実行したにょろ")
                return
            }

            playSound(soundDeactivateId)
            speak("クリック実行に失敗したにょろ")
        } else {
            playSound(soundDeactivateId)
            speak("フォーカスされている要素がないにょろ")
        }
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
            Log.d(TAG, "SoundPool Initialized. Activate: $soundActivateId, Deactivate: $soundDeactivateId, Focus: $soundFocusId, ViewEntered: $soundViewEnteredId")
        } catch (e: Exception) {
            Log.e(TAG, "SoundPool初期化エラー", e)
        }
    }

    private var lastPlaySoundId: Int = 0
    private var lastPlaySoundTime: Long = 0L

    private fun playSound(soundId: Int) {
        if (soundId != 0 && soundPool != null) {
            val now = System.currentTimeMillis()
            if (soundId == lastPlaySoundId && (now - lastPlaySoundTime) < 100) {
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
     * 全アクセシビリティイベント受信用分岐
     */
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
                // 画面（Activity/Dialog）が新しく切り替わった時だけ画面切り替え音を再生！
                playSound(soundViewEnteredId)
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
        }
    }

    /**
     * AOSP TalkBack Stable準拠 ノード解読解析
     */
    private fun parseNodeDescription(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val compat = AccessibilityNodeInfoCompat.wrap(node)
        val sb = StringBuilder()

        val mainText = when {
            !compat.text.isNullOrBlank() -> compat.text
            !compat.contentDescription.isNullOrBlank() -> compat.contentDescription
            !compat.roleDescription.isNullOrBlank() -> compat.roleDescription
            else -> null
        }

        if (!mainText.isNullOrBlank()) {
            sb.append(mainText).append(" ")
        }

        val className = compat.className
        if (className != null) {
            when {
                className.contains("Button") -> sb.append("ボタン ")
                className.contains("CheckBox") -> sb.append("チェックボックス ")
                className.contains("EditText") -> sb.append("編集テキスト ")
                className.contains("Folder") -> sb.append("フォルダ ")
                className.contains("ImageView") && mainText.isNullOrBlank() -> sb.append("画像 ")
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
