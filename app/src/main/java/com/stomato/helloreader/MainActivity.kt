package com.stomato.helloreader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stomato.helloreader.ui.theme.HelloreaderTheme
import java.util.Locale

import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // 1. 初回起動時に要求する権限一覧
    private val requiredPermissions: Array<String>
        get() {
            val list = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return list.toTypedArray()
        }

    // 2. 権限要求ランチャー
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) allGranted = false
        }
        if (allGranted) {
            speak("必要な権限が許可されました。次にアクセシビリティを有効化してください。")
        } else {
            speak("一部の権限が未許可です。電話帳の名前を読み上げるには権限が必要です。")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getBooleanExtra("EXTRA_REQUEST_PIN_UNLOCK", false) == true) {
            requestPinUnlockScreen()
        }

        // 自前TTSの初期化 (TalkBackがOFFでもアプリ単体で自発的に音声案内！)
        tts = TextToSpeech(applicationContext, this)

        // アプリ起動時に未許可の権限があれば自動で標準許可ダイアログをオープン！
        checkAndRequestPermissions()

        setContent {
            HelloreaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HelloReaderApp(
                        onRequestPermissions = {
                            speak("電話帳の相手の名前を読み上げるための Android 標準許可ダイアログが開きます。操作が難しい場合は、一時的に TalkBack をオンにするか、サポートの方に許可ボタンを押してもらってください。画面中央右下に許可ボタンがあります。")
                            mainHandler.postDelayed({
                                checkAndRequestPermissions()
                            }, 3500)
                        },
                        onOpenAccessibilitySettings = {
                            speak("アクセシビリティ設定画面へご案内します。ダウンロードしたアプリから Hello Reader を選択してオンにしてください。もし設定が難しい場合は、音量ボタンの上下を3秒間同時に長押しして TalkBack をオンにするか、サポートの方に設定を頼んでください。")
                            mainHandler.postDelayed({
                                openAccessibilitySettings()
                            }, 4500)
                        }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                Log.d("MainActivity", "自前TTS初期化成功！")
                // 智浩さんの自作メッセージをベースにしたTalkBack無効時対応の極上ガイダンス！
                speak("Hello Readerへようこそ！初期設定のご案内です。この画面には二つの大きなボタンとフォネティック・モールス設定があります。1のボタンで権限を許可し、2のボタンでアクセシビリティを有効にしてください。操作が難しい場合は、音量ボタンの上下を3秒間同時に長押しして TalkBack をオンにするか、お近くの方にボタンを押してもらってください。")
            }
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MainActivityTTS")
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("EXTRA_REQUEST_PIN_UNLOCK", false) == true) {
            requestPinUnlockScreen()
        }
    }

    fun requestPinUnlockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissError() {
                    Log.e("MainActivity", "Keyguard dismiss error")
                }
                override fun onDismissSucceeded() {
                    Log.d("MainActivity", "Keyguard dismiss succeeded")
                }
            })
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun HelloReaderApp(
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = (selectedTab == 0),
                    onClick = { selectedTab = 0 },
                    label = { Text("初期設定") },
                    icon = { Text("⚙️", fontSize = 18.sp) },
                    modifier = Modifier.semantics { contentDescription = "タブ1。初期設定画面を開きます" }
                )
                NavigationBarItem(
                    selected = (selectedTab == 1),
                    onClick = { selectedTab = 1 },
                    label = { Text("全アプリ対応") },
                    icon = { Text("📱", fontSize = 18.sp) },
                    modifier = Modifier.semantics { contentDescription = "タブ2。全アプリ対応プロファイル画面を開きます" }
                )
                NavigationBarItem(
                    selected = (selectedTab == 2),
                    onClick = { selectedTab = 2 },
                    label = { Text("カスタムラベル") },
                    icon = { Text("🏷️", fontSize = 18.sp) },
                    modifier = Modifier.semantics { contentDescription = "タブ3。カスタムラベル辞書画面を開きます" }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> PermissionSetupScreen(onRequestPermissions, onOpenAccessibilitySettings)
                1 -> AppProfilesScreen()
                2 -> CustomLabelsScreen()
            }
        }
    }
}

@Composable
fun PermissionSetupScreen(
    onRequestPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hello_reader_prefs", Context.MODE_PRIVATE) }
    
    var isPhoneticEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_phonetic_morse_enabled", false))
    }
    var timingMode by remember {
        mutableStateOf(prefs.getString("pref_phonetic_morse_timing", "AFTER_TTS") ?: "AFTER_TTS")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Hello Reader 設定",
            fontSize = 26.sp,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { contentDescription = "Hello Reader 設定ヘッダー" }
        )

        Text(
            text = "完全ハンズフリー＆全アプリ解読対応スクリーンリーダー",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // 1. 初期設定カード
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📋 初期セットアップ手順",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "ボタン1。必要なアクセス権限を許可するダイアログを開きます" }
                ) {
                    Text("1. 必要な権限を許可する", fontSize = 16.sp)
                }

                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "ボタン2。Androidのアクセシビリティ設定画面を開き、Hello Readerサービスを有効化します" }
                ) {
                    Text("2. アクセシビリティを有効化", fontSize = 16.sp)
                }
            }
        }

        // 2. 機能設定カード (フォネティック＆モールス設定)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "フォネティックコードとモールス信号機能。現在は ${if (isPhoneticEnabled) "オン" else "オフ"} です。" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚡ フォネティック＆モールス振動",
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = isPhoneticEnabled,
                        onCheckedChange = { checked ->
                            isPhoneticEnabled = checked
                            prefs.edit().putBoolean("pref_phonetic_morse_enabled", checked).apply()
                        }
                    )
                }

                if (isPhoneticEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(text = "発火タイミング設定:", fontSize = 14.sp, style = MaterialTheme.typography.labelLarge)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (timingMode == "AFTER_TTS"),
                            onClick = {
                                timingMode = "AFTER_TTS"
                                prefs.edit().putString("pref_phonetic_morse_timing", "AFTER_TTS").apply()
                            }
                        )
                        Text(text = "TTS音声の後にモールス振動", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (timingMode == "CONCURRENT"),
                            onClick = {
                                timingMode = "CONCURRENT"
                                prefs.edit().putString("pref_phonetic_morse_timing", "CONCURRENT").apply()
                            }
                        )
                        Text(text = "音声とモールス振動を同時に出力", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (timingMode == "BEFORE_TTS"),
                            onClick = {
                                timingMode = "BEFORE_TTS"
                                prefs.edit().putString("pref_phonetic_morse_timing", "BEFORE_TTS").apply()
                            }
                        )
                        Text(text = "モールス振動の後にTTS音声", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AppProfilesScreen() {
    val aospApps = listOf(
        "⚙️ AOSP / Google 設定" to "com.android.settings",
        "📞 AOSP / Google 電話" to "com.android.dialer",
        "👥 AOSP / Google 連絡先" to "com.android.contacts",
        "💬 AOSP / Google メッセージ" to "com.android.messaging",
        "⏰ AOSP / Google 時計" to "com.android.deskclock",
        "🧮 AOSP / Google 電卓" to "com.android.calculator2",
        "📷 AOSP / Google カメラ" to "com.android.camera2"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "📱 全アプリ対応プロファイル",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "AOSP標準アプリや各種アプリへの最適化プロファイル一覧です",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        aospApps.forEach { (appName, pkgName) ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = appName, fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                        Text(text = pkgName, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    SuggestionChip(
                        onClick = { },
                        label = { Text("有効 (Active)") }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomLabelsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "🏷️ カスタムラベル辞書",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "名無しの画像ボタン等に設定したカスタム読み上げラベル一覧です",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "登録済みラベル (プリセット)", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "• com.android.systemui -> 戻るボタン", fontSize = 14.sp)
                Text(text = "• com.android.systemui -> ホームボタン", fontSize = 14.sp)
                Text(text = "• com.android.systemui -> 最近のアプリボタン", fontSize = 14.sp)
            }
        }
    }
}