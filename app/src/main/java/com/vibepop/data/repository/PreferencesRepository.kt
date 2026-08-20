package com.vibepop.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.vibepop.data.model.PopupConfig

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vibepop_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DISMISS_SECONDS = "key_dismiss_seconds"
        private const val KEY_CUSTOM_NAME = "key_custom_name"
        private const val KEY_SERVICE_ENABLED = "key_service_enabled"
        private const val KEY_VIBRATION_ENABLED = "key_vibration_enabled"
        private const val KEY_FORCE_SPEAKERPHONE = "key_force_speakerphone"
        private const val KEY_ANIMATION_THEME = "key_animation_theme"
        private const val KEY_CUSTOM_MEDIA_PATH = "key_custom_media_path"
        private const val KEY_CUSTOM_MEDIA_TYPE = "key_custom_media_type"
        private const val KEY_VIDEO_DISMISS_MODE = "key_video_dismiss_mode"
        private const val KEY_TARGET_DEVICES = "key_target_devices"
    }

    fun getPopupConfig(): PopupConfig {
        return PopupConfig(
            autoDismissSeconds = prefs.getInt(KEY_DISMISS_SECONDS, 4),
            customDeviceName = prefs.getString(KEY_CUSTOM_NAME, "AirPods Pro 2") ?: "AirPods Pro 2",
            isServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true),
            isVibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
            isForceSpeakerphone = prefs.getBoolean(KEY_FORCE_SPEAKERPHONE, true),
            animationTheme = prefs.getString(KEY_ANIMATION_THEME, "classic_airpods") ?: "classic_airpods",
            customMediaPath = prefs.getString(KEY_CUSTOM_MEDIA_PATH, null),
            customMediaType = prefs.getString(KEY_CUSTOM_MEDIA_TYPE, "preset") ?: "preset",
            videoDismissMode = prefs.getString(KEY_VIDEO_DISMISS_MODE, "on_complete") ?: "on_complete",
            targetDeviceAddresses = HashSet(prefs.getStringSet(KEY_TARGET_DEVICES, emptySet()) ?: emptySet())
        )
    }

    fun saveAutoDismissSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_DISMISS_SECONDS, seconds).apply()
    }

    fun saveCustomDeviceName(name: String) {
        prefs.edit().putString(KEY_CUSTOM_NAME, name).apply()
    }

    fun saveServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun saveForceSpeakerphone(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_SPEAKERPHONE, enabled).apply()
    }

    fun saveAnimationTheme(theme: String) {
        prefs.edit().putString(KEY_ANIMATION_THEME, theme).apply()
    }

    fun saveCustomMedia(path: String?, type: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_MEDIA_PATH, path)
            .putString(KEY_CUSTOM_MEDIA_TYPE, type)
            .apply()
    }

    fun saveVideoDismissMode(mode: String) {
        prefs.edit().putString(KEY_VIDEO_DISMISS_MODE, mode).apply()
    }

    fun saveTargetDeviceAddresses(addresses: Set<String>) {
        prefs.edit().putStringSet(KEY_TARGET_DEVICES, HashSet(addresses)).apply()
    }
}
