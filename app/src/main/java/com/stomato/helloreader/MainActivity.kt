package com.stomato.helloreader

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stomato.helloreader.ui.theme.HelloreaderTheme
import androidx.compose.ui.text.font.FontWeight
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
                val prefs = getSharedPreferences("hello_reader_prefs", Context.MODE_PRIVATE)
                val isStartupGuidanceEnabled = prefs.getBoolean("pref_startup_guidance_enabled", false)
                if (isStartupGuidanceEnabled) {
                    speak("Hello Reader 初期設定画面")
                }
            }
        }
    }

    private fun speak(text: String) {
        // MainActivity 自前の単独発声を廃止し、HelloReaderService の一括制御に統合！
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("EXTRA_REQUEST_PIN_UNLOCK", false)) {
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
                NavigationBarItem(
                    selected = (selectedTab == 3),
                    onClick = { selectedTab = 3 },
                    label = { Text("サポート") },
                    icon = { Text("🆘", fontSize = 18.sp) },
                    modifier = Modifier.semantics { contentDescription = "タブ4。サポート・お問い合わせ画面を開きます" }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> PermissionSetupScreen(onRequestPermissions, onOpenAccessibilitySettings)
                1 -> AppProfilesScreen()
                2 -> CustomLabelsScreen()
                3 -> SupportScreen()
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
    var phoneticDictMode by remember {
        mutableStateOf(prefs.getString("pref_phonetic_dictionary_mode", "TRADITIONAL") ?: "TRADITIONAL")
    }
    var isTimeSignalEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_time_signal_enabled", true))
    }
    var isBrailleEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_braille_display_enabled", true))
    }
    var isHapticEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_haptic_feedback_enabled", true))
    }
    var isBrailleHapticEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_braille_haptic_enabled", true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                .padding(bottom = 20.dp)
                .semantics {
                    heading()
                    contentDescription = "Hello Reader 設定ヘッダー"
                }
        )

        // 1. 初期セットアップ手順カード
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
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .semantics { heading() }
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
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "ボタン2。Androidのアクセシビリティ設定画面を開き、Hello Readerサービスを有効化します" }
                ) {
                    Text("2. アクセシビリティを有効化", fontSize = 16.sp)
                }

                var showBrailleDialog by remember { mutableStateOf(false) }

                Button(
                    onClick = { showBrailleDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "ボタン3。ブレールメモやブレイルセンスなどの点字ディスプレイを検索・ペアリング接続する画面を開きます" }
                ) {
                    Text("3. 点字ディスプレイを検索・ペアリング (BMS / BS6)", fontSize = 15.sp)
                }

                if (showBrailleDialog) {
                    BrailleDeviceScanDialog(
                        onDismiss = { showBrailleDialog = false }
                    )
                }
            }
        }

        // 2. モジュール機能設定カード (時報・点字・触覚フィードバック)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚙️ 支援モジュール設定",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .semantics { heading() }
                )

                // 時報機能
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = "スマート時報・チャイム機能。現在は ${if (isTimeSignalEnabled) "オン" else "オフ"} です。" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "⏰ 毎時0分の時報・チャイム", fontSize = 15.sp)
                        Text(text = "毎時0分に優しく時刻をお知らせします", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = isTimeSignalEnabled,
                        onCheckedChange = { checked ->
                            isTimeSignalEnabled = checked
                            prefs.edit().putBoolean("pref_time_signal_enabled", checked).apply()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 点字ディスプレイ機能
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = "点字ディスプレイ連携。現在は ${if (isBrailleEnabled) "オン" else "オフ"} です。" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "⠃⠗⠇ 点字ディスプレイ連携", fontSize = 15.sp)
                        Text(text = "BrailleSense 6 Mini / 7 等の点字出力サポート", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = isBrailleEnabled,
                        onCheckedChange = { checked ->
                            isBrailleEnabled = checked
                            prefs.edit().putBoolean("pref_braille_display_enabled", checked).apply()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 触覚フィードバック機能
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = "触覚フィードバック。現在は ${if (isHapticEnabled) "オン" else "オフ"} です。" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "📳 触覚フィードバック (振動)", fontSize = 15.sp)
                        Text(text = "フォーカス移動やボタンタップ時の振動案内", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = isHapticEnabled,
                        onCheckedChange = { checked ->
                            isHapticEnabled = checked
                            prefs.edit().putBoolean("pref_haptic_feedback_enabled", checked).apply()
                        }
                    )
                }
            }
        }

        // 3. キーボード詳細入力支援（点字触覚＆モールス振動）設定カード
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⌨️ キーボード詳細入力支援",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .semantics { heading() }
                )

                // 点字触覚パルス振動機能
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = "キーボード表示時の点字触覚フィードバック。現在は ${if (isBrailleHapticEnabled) "オン" else "オフ"} です。" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "⠃⠗⠇ 点字触覚パルス振動 (IME表示時)", fontSize = 15.sp)
                        Text(text = "キーボード表示中（半角英数・ローマ字・かな）に文字の6点点字パルスを振動出力します", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = isBrailleHapticEnabled,
                        onCheckedChange = { checked ->
                            isBrailleHapticEnabled = checked
                            prefs.edit().putBoolean("pref_braille_haptic_enabled", checked).apply()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // モールス信号機能
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "フォネティックコードとモールス信号機能。現在は ${if (isPhoneticEnabled) "オン" else "オフ"} です。" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "⚡ フォネティック＆モールス振動", fontSize = 15.sp)
                        Text(text = "入力エリアでのモールス符号振動", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(text = "通話票辞書の選択:", fontSize = 14.sp, style = MaterialTheme.typography.labelLarge)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (phoneticDictMode == "TRADITIONAL"),
                            onClick = {
                                phoneticDictMode = "TRADITIONAL"
                                prefs.edit().putString("pref_phonetic_dictionary_mode", "TRADITIONAL").apply()
                            }
                        )
                        Text(text = "伝統通話表（和文・伝統、例: うえののウ） [デフォルト]", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (phoneticDictMode == "STANDARD"),
                            onClick = {
                                phoneticDictMode = "STANDARD"
                                prefs.edit().putString("pref_phonetic_dictionary_mode", "STANDARD").apply()
                            }
                        )
                        Text(text = "標準通話表（朝日・現代、例: あさひのア）", fontSize = 13.sp)
                    }
                }
            }
        }

        // 4. 診断ログ作成・SOS問い合わせカード
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "✉️ 診断ログ作成・SOS問い合わせ",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "動作の不具合や困りごとがある場合、端末診断ログを添えてメーラーから連絡できます。個人情報や電話番号は一切含まれません。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Button(
                    onClick = {
                        launchDiagnosticMailIntent(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "ボタン。端末診断ログを作成し、メールアプリを起動して問い合わせを作成します。" }
                ) {
                    Text("診断ログを生成してメール送信 (SOS)", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun SupportScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🆘 サポート・お問い合わせ",
            fontSize = 26.sp,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .padding(bottom = 20.dp)
                .semantics {
                    heading()
                    contentDescription = "サポートとお問い合わせヘッダー"
                }
        )

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "✉️ お問い合わせのご案内",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { heading() }
                )
                Text(
                    text = "問い合わせ・ご意見は下記のボタンを押して表示されるメール作成画面の本文に入力して送信してください。返信は、順次開発者より行います。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        launchDiagnosticMailIntent(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "ボタン。端末診断ログを含むメール作成画面を開きます。" }
                ) {
                    Text("メール作成画面を開く (SOS)", fontSize = 16.sp)
                }
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔒 診断レポートとプライバシー保護",
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { heading() }
                )
                Text(
                    text = "メール本文には、不具合調査のために以下の情報が自動添付されます:\n" +
                            "・OSバージョン / 端末機種名\n" +
                            "・匿名 b3sum 暗号識別ハッシュ\n" +
                            "・アプリの各機能設定状態\n" +
                            "・直近動作ログ (最新20件)\n\n" +
                            "氏名や電話番号、個人の入力内容は一切含まれません。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * 難読化されたサポート用メールアドレスをデコードし、
 * b3sum 形式の暗号ハッシュを含む診断ログを添付してメーラーを起動します。
 */
fun launchDiagnosticMailIntent(context: Context) {
    val report = HelloReaderService.generateDiagnosticReport(context)
    
    // Base64 難読化されたサポート用メールアドレス
    val encodedEmail = "c3RvbWF0bzQ2M0BnbWFpbC5jb20="
    val targetEmail = try {
        String(android.util.Base64.decode(encodedEmail, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: Exception) {
        ""
    }

    val uriText = "mailto:${android.net.Uri.encode(targetEmail)}?" +
            "subject=${android.net.Uri.encode("■hello-reader問い合わせ・ご意見")}&" +
            "body=${android.net.Uri.encode(report)}"

    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(uriText)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        val chooserIntent = Intent.createChooser(intent, "メールアプリを選択してください").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "メーラーの起動に失敗しました。診断ログをクリップボードにコピーしました",
            android.widget.Toast.LENGTH_LONG
        ).show()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Hello Reader Diagnostic Report", report)
        clipboard?.setPrimaryClip(clip)
    }
}

@Composable
fun AppProfilesScreen() {
    val aospApps = listOf(
        "⚙️ AOSP / Google 設定" to "com.android.settings",
        "📞 AOSP / Google 電話" to "com.google.android.dialer",
        "👥 AOSP / Google 連絡先" to "com.google.android.contacts",
        "💬 AOSP / Google メッセージ" to "com.google.android.apps.messaging",
        "⏰ AOSP / Google 時計" to "com.google.android.deskclock",
        "🧮 AOSP / Google 電卓" to "com.google.android.calculator",
        "📷 AOSP / Google カメラ" to "com.google.android.GoogleCamera"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
    val context = LocalContext.current
    val customLabelManager = remember { com.stomato.helloreader.appsupport.CustomLabelManager(context) }
    var pronunciations by remember { mutableStateOf(customLabelManager.getAllPronunciations()) }

    var newOriginal by remember { mutableStateOf("") }
    var newReading by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "🏷️ ユーザー読み辞書エディタ",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "読み間違えやすい単語や人名、駅名の読み方を自由にカスタマイズできます",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. 新規単語登録カード
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "➕ 新しい読み仮名を登録", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newOriginal,
                    onValueChange = { newOriginal = it },
                    label = { Text("元の表記（例: 大垣）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newReading,
                    onValueChange = { newReading = it },
                    label = { Text("読み上げ（例: おおがき）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (newOriginal.isNotBlank() && newReading.isNotBlank()) {
                            customLabelManager.addPronunciation(newOriginal.trim(), newReading.trim())
                            pronunciations = customLabelManager.getAllPronunciations()
                            newOriginal = ""
                            newReading = ""
                            android.widget.Toast.makeText(context, "辞書に登録しました", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("辞書に追加する")
                }
            }
        }

        // 2. 登録済み一覧カード
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "📖 登録済み読み替え一覧 (${pronunciations.size}件)", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                pronunciations.forEach { (orig, read) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = orig, fontSize = 15.sp, style = MaterialTheme.typography.titleSmall)
                            Text(text = "➔ $read", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = {
                                customLabelManager.removePronunciation(orig)
                                pronunciations = customLabelManager.getAllPronunciations()
                                android.widget.Toast.makeText(context, "削除しました", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("🗑️", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrailleDeviceScanDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val brailleBtManager = remember {
        com.stomato.helloreader.braille.BrailleBluetoothManager(
            context = context,
            onDataReceived = { },
            onConnectionStateChanged = { _, _ -> }
        )
    }
    var pairedDevices by remember { mutableStateOf(brailleBtManager.getPairedDevices()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "📟 点字ディスプレイ検索・接続",
                fontSize = 20.sp,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "ブレールメモ (KGS BMS/BM) や ブレイルセンス (HIMS) などの点字ディスプレイを選択してペアリング接続します。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "【ペアリング済み点字端末】 (${pairedDevices.size}件)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (pairedDevices.isEmpty()) {
                    Text(
                        text = "ペアリング済みのBluetooth点字端末が見つかりません。下のボタンから新しい端末を検索・追加してください。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    pairedDevices.forEach { device ->
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "${device.name ?: "点字端末"} に接続を試みます...", Toast.LENGTH_SHORT).show()
                                brailleBtManager.connectToDevice(device)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .semantics { contentDescription = "点字端末 ${device.name ?: "不明な機器"} にワンタップで接続します" }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📟 ${device.name ?: "不明な点字端末"}", fontSize = 14.sp)
                                Text("接続", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Bluetooth設定を開けませんでした", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "Android標準のBluetooth新しい機器をペアリング画面を開きます" }
                ) {
                    Text("🔍 新しいBluetooth端末を検索・追加", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}