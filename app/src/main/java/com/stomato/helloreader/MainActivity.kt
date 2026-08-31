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
                    PermissionSetupScreen(
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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Hello Reader 設定へようこそ",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .semantics { contentDescription = "Hello Reader 設定へようこそ" }
        )

        Text(
            text = "Hello Reader を使用するには、電話帳照会権限の許可とアクセシビリティサービスの有効化が必要です。",
            fontSize = 16.sp,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .semantics { contentDescription = "Hello Reader を使用するには、電話帳照会権限の許可とアクセシビリティサービスの有効化が必要です。" }
        )

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 12.dp)
                .semantics { contentDescription = "権限の許可ダイアログを再表示するボタン" }
        ) {
            Text("1. 必要な権限を許可する", fontSize = 18.sp)
        }

        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 24.dp)
                .semantics { contentDescription = "Androidアクセシビリティ設定を開くボタン" }
        ) {
            Text("2. アクセシビリティを有効化", fontSize = 18.sp)
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // フォネティック＆モールス設定エリア (デフォルト OFF)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "フォネティックコードとモールス信号機能。現在は ${if (isPhoneticEnabled) "オン" else "オフ"} です。" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "フォネティック＆モールス振動機能 (標準:OFF)",
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(text = "発火タイミング設定:", fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (timingMode == "AFTER_TTS"),
                        onClick = {
                            timingMode = "AFTER_TTS"
                            prefs.edit().putString("pref_phonetic_morse_timing", "AFTER_TTS").apply()
                        }
                    )
                    Text(text = "TTSの後にモールス", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (timingMode == "CONCURRENT"),
                        onClick = {
                            timingMode = "CONCURRENT"
                            prefs.edit().putString("pref_phonetic_morse_timing", "CONCURRENT").apply()
                        }
                    )
                    Text(text = "同時発火", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (timingMode == "BEFORE_TTS"),
                        onClick = {
                            timingMode = "BEFORE_TTS"
                            prefs.edit().putString("pref_phonetic_morse_timing", "BEFORE_TTS").apply()
                        }
                    )
                    Text(text = "モールスの後にTTS", fontSize = 12.sp)
                }
            }
        }
    }
}