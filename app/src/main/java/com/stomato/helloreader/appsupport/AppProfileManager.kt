package com.stomato.helloreader.appsupport

import android.util.Log
import com.stomato.helloreader.appsupport.profiles.*

/**
 * パッケージ名に応じた AppProfile を検索・管理するマネージャー
 */
class AppProfileManager {

    companion object {
        private const val TAG = "AppProfileManager"
    }

    private val defaultProfile = DefaultAppProfile()
    private val profilesMap = mutableMapOf<String, AppProfile>()

    init {
        // AOSP & Google 標準アプリプロファイルの登録
        registerProfiles()
    }

    private fun registerProfiles() {
        registerProfile(AospSettingsProfile())
        registerProfile(AospDialerProfile("com.android.dialer"))
        registerProfile(AospDialerProfile("com.google.android.dialer"))
        registerProfile(AospContactsProfile("com.android.contacts"))
        registerProfile(AospContactsProfile("com.google.android.contacts"))
        registerProfile(AospMessagingProfile("com.android.messaging"))
        registerProfile(AospMessagingProfile("com.google.android.apps.messaging"))
        registerProfile(AospClockProfile("com.android.deskclock"))
        registerProfile(AospClockProfile("com.google.android.deskclock"))
        registerProfile(AospCalculatorProfile("com.android.calculator2"))
        registerProfile(AospCalculatorProfile("com.google.android.calculator"))
        registerProfile(AospCameraProfile("com.android.camera2"))
        registerProfile(AospCameraProfile("com.google.android.GoogleCamera"))
    }

    fun registerProfile(profile: AppProfile) {
        profilesMap[profile.targetPackageName] = profile
        Log.d(TAG, "Registered AppProfile for package: ${profile.targetPackageName}")
    }

    /**
     * パッケージ名に対応する AppProfile を取得する
     */
    fun getProfileForPackage(packageName: String?): AppProfile {
        if (packageName.isNullOrBlank()) return defaultProfile
        return profilesMap[packageName] ?: defaultProfile
    }
}
