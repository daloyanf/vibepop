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
        private const val KEY_TARGET_POLICY_NOTICED = "key_target_policy_noticed"

        // 耳机专属独立配置键前缀
        private const val PREFIX_DEV_NAME = "key_dev_name_"
        private const val PREFIX_DEV_THEME = "key_dev_theme_"
        private const val PREFIX_DEV_MEDIA_PATH = "key_dev_media_path_"
        private const val PREFIX_DEV_MEDIA_TYPE = "key_dev_media_type_"
        private const val PREFIX_DEV_VIDEO_DISMISS = "key_dev_video_dismiss_"
    }

    /**
     * 全局默认弹窗配置
     */
    fun getPopupConfig(): PopupConfig {
        return PopupConfig(
            autoDismissSeconds = prefs.getInt(KEY_DISMISS_SECONDS, 4),
            customDeviceName = prefs.getString(KEY_CUSTOM_NAME, "") ?: "",
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

    /**
     * 获取指定耳机（按 MAC 地址）的专属弹窗配置
     */
    fun getDevicePopupConfig(deviceAddress: String): PopupConfig {
        val globalConfig = getPopupConfig()
        if (deviceAddress.isBlank()) return globalConfig

        val cleanAddress = sanitizeAddress(deviceAddress)
        val devCustomName = prefs.getString(PREFIX_DEV_NAME + cleanAddress, null) ?: globalConfig.customDeviceName
        val devTheme = prefs.getString(PREFIX_DEV_THEME + cleanAddress, null) ?: globalConfig.animationTheme
        val devMediaPath = prefs.getString(PREFIX_DEV_MEDIA_PATH + cleanAddress, null) ?: globalConfig.customMediaPath
        val devMediaType = prefs.getString(PREFIX_DEV_MEDIA_TYPE + cleanAddress, null) ?: globalConfig.customMediaType
        val devVideoDismiss = prefs.getString(PREFIX_DEV_VIDEO_DISMISS + cleanAddress, null) ?: globalConfig.videoDismissMode

        return globalConfig.copy(
            customDeviceName = devCustomName,
            animationTheme = devTheme,
            customMediaPath = devMediaPath,
            customMediaType = devMediaType,
            videoDismissMode = devVideoDismiss
        )
    }

    /**
     * 保存指定耳机的专属弹窗配置
     */
    fun saveDevicePopupConfig(deviceAddress: String, config: PopupConfig) {
        if (deviceAddress.isBlank()) return
        val cleanAddress = sanitizeAddress(deviceAddress)
        prefs.edit()
            .putString(PREFIX_DEV_NAME + cleanAddress, config.customDeviceName)
            .putString(PREFIX_DEV_THEME + cleanAddress, config.animationTheme)
            .putString(PREFIX_DEV_MEDIA_PATH + cleanAddress, config.customMediaPath)
            .putString(PREFIX_DEV_MEDIA_TYPE + cleanAddress, config.customMediaType)
            .putString(PREFIX_DEV_VIDEO_DISMISS + cleanAddress, config.videoDismissMode)
            .apply()
    }

    fun saveDeviceCustomName(deviceAddress: String, name: String) {
        if (deviceAddress.isBlank()) return
        prefs.edit().putString(PREFIX_DEV_NAME + sanitizeAddress(deviceAddress), name).apply()
    }

    fun saveDeviceMedia(deviceAddress: String, path: String?, type: String) {
        if (deviceAddress.isBlank()) return
        val clean = sanitizeAddress(deviceAddress)
        prefs.edit()
            .putString(PREFIX_DEV_MEDIA_PATH + clean, path)
            .putString(PREFIX_DEV_MEDIA_TYPE + clean, type)
            .apply()
    }

    private fun sanitizeAddress(address: String): String {
        return address.replace(":", "_").uppercase()
    }

    /**
     * 弹窗响应策略变更（默认全不响应）的一次性升级提示。
     * 仅在旧版本已有配置数据（升级用户）时返回 true；全新安装直接标记已提示。
     */
    fun consumeTargetPolicyNotice(): Boolean {
        if (prefs.getBoolean(KEY_TARGET_POLICY_NOTICED, false)) return false
        val isUpgradeUser = prefs.all.isNotEmpty()
        prefs.edit().putBoolean(KEY_TARGET_POLICY_NOTICED, true).apply()
        return isUpgradeUser
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

