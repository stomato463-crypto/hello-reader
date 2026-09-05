package com.stomato.helloreader.appsupport

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale

/**
 * 診断ログ用の匿名識別ハッシュ生成ユーティリティ
 * 端末固有情報やシリアル番号を暗号学的ハッシュ関数 (SHA-256 / BLAKE3 スタイル) で非逆転化し、
 * 個人情報を完全に保護した状態でサポート診断用の匿名IDを生成します。
 */
object DiagnosticHasher {

    /**
     * 端末の環境情報から 64 文字の匿名識別ハッシュダイジェストを生成します。
     *
     * @param context アプリケーションコンテキスト
     * @return 64桁の16進数ハッシュ文字列 (例: b3a4f8...)
     */
    fun generateAnonymousHash(context: Context): String {
        val rawInput = try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_android_id"
            val board = Build.BOARD ?: "unknown_board"
            val brand = Build.BRAND ?: "unknown_brand"
            val device = Build.DEVICE ?: "unknown_device"
            val hardware = Build.HARDWARE ?: "unknown_hardware"
            val model = Build.MODEL ?: "unknown_model"

            "$androidId:$board:$brand:$device:$hardware:$model"
        } catch (e: Exception) {
            "fallback_diagnostic_seed_${System.currentTimeMillis()}"
        }

        return sha256Hex(rawInput)
    }

    /**
     * 指定された文字列を SHA-256 でハッシュ化し、16進数文字列として返します。
     */
    private fun sha256Hex(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hashBytes) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString().lowercase(Locale.ROOT)
        } catch (e: Exception) {
            "0000000000000000000000000000000000000000000000000000000000000000"
        }
    }
}
