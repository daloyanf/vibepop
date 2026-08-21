package com.vibepop.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.vibepop.R
import com.vibepop.data.model.DeviceBatteryState
import com.vibepop.data.model.PopupConfig
import com.vibepop.data.repository.PreferencesRepository
import com.vibepop.overlay.PopupWindowManager
import com.vibepop.service.HeadsetMonitorService
import com.vibepop.util.PermissionHelper
import java.io.File
import java.io.FileOutputStream

data class BondedDeviceItem(
    val name: String,
    val address: String,
    val isSelected: Boolean,
    val customName: String = "",
    val animationTheme: String = "classic_airpods",
    val customMediaPath: String? = null,
    val customMediaType: String = "preset",
    val videoDismissMode: String = "on_complete"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PreferencesRepository(application)

    private val _popupConfig = MutableLiveData<PopupConfig>()
    val popupConfig: LiveData<PopupConfig> = _popupConfig

    private val _isOverlayGranted = MutableLiveData<Boolean>()
    val isOverlayGranted: LiveData<Boolean> = _isOverlayGranted

    private val _isBluetoothGranted = MutableLiveData<Boolean>()
    val isBluetoothGranted: LiveData<Boolean> = _isBluetoothGranted

    private val _isNotificationGranted = MutableLiveData<Boolean>()
    val isNotificationGranted: LiveData<Boolean> = _isNotificationGranted

    private val _isMediaGranted = MutableLiveData<Boolean>()
    val isMediaGranted: LiveData<Boolean> = _isMediaGranted

    private val _isOemGranted = MutableLiveData<Boolean>()
    val isOemGranted: LiveData<Boolean> = _isOemGranted

    private val _isServiceRunning = MutableLiveData<Boolean>()
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning

    private val _bondedDevices = MutableLiveData<List<BondedDeviceItem>>()
    val bondedDevices: LiveData<List<BondedDeviceItem>> = _bondedDevices

    init {
        loadConfig()
    }

    fun loadConfig() {
        _popupConfig.value = repository.getPopupConfig()
    }

    fun refreshPermissions(context: Context) {
        _isOverlayGranted.value = PermissionHelper.hasOverlayPermission(context)
        _isBluetoothGranted.value = PermissionHelper.hasBluetoothPermission(context)
        _isNotificationGranted.value = PermissionHelper.hasNotificationPermission(context)
        _isMediaGranted.value = PermissionHelper.hasMediaPermission(context)
        _isOemGranted.value = PermissionHelper.hasOemLockScreenPermission(context)

        val config = repository.getPopupConfig()
        if (config.isServiceEnabled && !HeadsetMonitorService.isServiceRunning) {
            try {
                HeadsetMonitorService.start(context)
            } catch (e: Exception) {}
        }
        _isServiceRunning.value = HeadsetMonitorService.isServiceRunning || config.isServiceEnabled
        refreshBondedDevices(context)
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices(context: Context) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            @Suppress("DEPRECATION")
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && PermissionHelper.hasBluetoothPermission(context)) {
                val targets = _popupConfig.value?.targetDeviceAddresses ?: emptySet()
                val list = adapter.bondedDevices?.map { device ->
                    val unknownName = context.getString(R.string.device_name_unknown)
                    val name = try { device.name ?: unknownName } catch (e: Exception) { unknownName }
                    val address = device.address ?: ""
                    val devConfig = repository.getDevicePopupConfig(address)
                    BondedDeviceItem(
                        name = name,
                        address = address,
                        isSelected = targets.contains(address),
                        customName = devConfig.customDeviceName,
                        animationTheme = devConfig.animationTheme,
                        customMediaPath = devConfig.customMediaPath,
                        customMediaType = devConfig.customMediaType,
                        videoDismissMode = devConfig.videoDismissMode
                    )
                } ?: emptyList()
                _bondedDevices.value = list
            }
        } catch (e: Exception) {
            _bondedDevices.value = emptyList()
        }
    }

    fun updateDeviceName(name: String) {
        repository.saveCustomDeviceName(name)
        _popupConfig.value = _popupConfig.value?.copy(customDeviceName = name)
    }

    fun updateDismissSeconds(seconds: Int) {
        repository.saveAutoDismissSeconds(seconds)
        _popupConfig.value = _popupConfig.value?.copy(autoDismissSeconds = seconds)
    }

    fun updateVideoDismissMode(mode: String) {
        repository.saveVideoDismissMode(mode)
        _popupConfig.value = _popupConfig.value?.copy(videoDismissMode = mode)
    }

    fun updateForceSpeakerphone(enabled: Boolean) {
        repository.saveForceSpeakerphone(enabled)
        _popupConfig.value = _popupConfig.value?.copy(isForceSpeakerphone = enabled)
    }

    fun updateAnimationTheme(theme: String) {
        repository.saveAnimationTheme(theme)
        if (theme != "custom_media") {
            repository.saveCustomMedia(null, "preset")
            _popupConfig.value = _popupConfig.value?.copy(
                animationTheme = theme,
                customMediaPath = null,
                customMediaType = "preset"
            )
        } else {
            _popupConfig.value = _popupConfig.value?.copy(animationTheme = theme)
        }
    }

    /**
     * 读取指定耳机的专属弹窗配置
     */
    fun getDevicePopupConfig(address: String): PopupConfig {
        return repository.getDevicePopupConfig(address)
    }

    /**
     * 保存指定耳机的专属弹窗配置并刷新列表
     */
    fun saveDevicePopupConfig(context: Context, address: String, config: PopupConfig) {
        repository.saveDevicePopupConfig(address, config)
        refreshBondedDevices(context)
    }

    /**
     * 为指定耳机导入专属相册媒体
     */
    fun importDeviceMedia(context: Context, address: String, uri: Uri): Pair<Boolean, String> {
        return try {
            val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val type = when {
                mimeType.startsWith("video/") -> "video"
                mimeType.startsWith("image/") -> "image"
                mimeType.contains("json") -> "lottie"
                else -> {
                    val path = uri.path?.lowercase() ?: ""
                    when {
                        path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") -> "video"
                        path.endsWith(".gif") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".webp") || path.endsWith(".jpeg") -> "image"
                        path.endsWith(".json") -> "lottie"
                        else -> "image"
                    }
                }
            }

            val ext = resolveMediaExtension(mimeType, uri, type)

            val safeAddress = address.replace(":", "_").uppercase()
            val destFile = File(context.filesDir, "device_media_${safeAddress}$ext")
            val inputStream = context.contentResolver.openInputStream(uri) ?: return Pair(false, "")
            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }

            val mediaPath = destFile.absolutePath
            repository.saveDeviceMedia(address, mediaPath, type)
            refreshBondedDevices(context)
            Pair(true, type)
        } catch (e: Exception) {
            Pair(false, "")
        }
    }

    /**
     * 一键预览指定耳机的专属弹窗
     */
    fun previewDevicePopup(context: Context, address: String) {
        val devConfig = repository.getDevicePopupConfig(address)
        val mockState = DeviceBatteryState(
            deviceName = devConfig.customDeviceName,
            deviceAddress = address,
            isConnected = true,
            batteryLevel = 85,
            isCharging = false
        )
        PopupWindowManager.showPopup(context, mockState, devConfig)
    }

    /**
     * 导入本地全局媒体（MP4 视频 / GIF 动图 / WebP / PNG / JPG / JSON 矢量）
     */
    fun importCustomMedia(context: Context, uri: Uri): Pair<Boolean, String> {
        return try {
            val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val type = when {
                mimeType.startsWith("video/") -> "video"
                mimeType.startsWith("image/") -> "image"
                mimeType.contains("json") -> "lottie"
                else -> {
                    val path = uri.path?.lowercase() ?: ""
                    when {
                        path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") -> "video"
                        path.endsWith(".gif") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".webp") || path.endsWith(".jpeg") -> "image"
                        path.endsWith(".json") -> "lottie"
                        else -> "image"
                    }
                }
            }

            val ext = resolveMediaExtension(mimeType, uri, type)

            val destFile = File(context.filesDir, "custom_popup_media$ext")
            val inputStream = context.contentResolver.openInputStream(uri) ?: return Pair(false, "")
            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }

            val mediaPath = destFile.absolutePath
            repository.saveCustomMedia(mediaPath, type)
            repository.saveAnimationTheme("custom_media")
            _popupConfig.value = _popupConfig.value?.copy(
                customMediaPath = mediaPath,
                customMediaType = type,
                animationTheme = "custom_media"
            )
            Pair(true, type)
        } catch (e: Exception) {
            Pair(false, "")
        }
    }

    fun toggleTargetDevice(address: String) {
        val currentTargets = (_popupConfig.value?.targetDeviceAddresses ?: emptySet()).toMutableSet()
        if (currentTargets.contains(address)) {
            currentTargets.remove(address)
        } else {
            currentTargets.add(address)
        }
        repository.saveTargetDeviceAddresses(currentTargets)
        _popupConfig.value = _popupConfig.value?.copy(targetDeviceAddresses = currentTargets)

        _bondedDevices.value = _bondedDevices.value?.map {
            if (it.address == address) it.copy(isSelected = currentTargets.contains(address)) else it
        }
    }

    fun clearAllTargetDevices() {
        val emptyTargets = emptySet<String>()
        repository.saveTargetDeviceAddresses(emptyTargets)
        _popupConfig.value = _popupConfig.value?.copy(targetDeviceAddresses = emptyTargets)
        _bondedDevices.value = _bondedDevices.value?.map { it.copy(isSelected = false) }
    }

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        repository.saveServiceEnabled(enabled)
        _popupConfig.value = _popupConfig.value?.copy(isServiceEnabled = enabled)
        if (enabled) {
            HeadsetMonitorService.start(context)
        } else {
            HeadsetMonitorService.stop(context)
        }
        _isServiceRunning.value = enabled
    }

    fun triggerMockPopup(context: Context) {
        val config = _popupConfig.value ?: repository.getPopupConfig()
        val displayName = if (config.customDeviceName.isNotBlank()) config.customDeviceName else context.getString(R.string.device_name_fallback)
        val mockState = DeviceBatteryState(
            deviceName = displayName,
            deviceAddress = "00:11:22:33:44:55",
            isConnected = true,
            batteryLevel = 85,
            isCharging = false
        )
        PopupWindowManager.showPopup(context, mockState, config)
    }

    /**
     * 弹窗响应策略变更（默认全不响应）一次性提示（供 MainActivity 消费）
     */
    fun consumeTargetPolicyNotice(): Boolean {
        return repository.consumeTargetPolicyNotice()
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("gif", "png", "jpg", "jpeg", "webp", "bmp", "heic", "heif")

        /**
         * 根据 MIME 类型与 URI 路径推导目标文件扩展名，
         * 尽量保留原始图片扩展名，避免 JPG/WebP 内容被误存为 .png
         */
        private fun resolveMediaExtension(mimeType: String, uri: Uri, type: String): String = when (type) {
            "video" -> ".mp4"
            "lottie" -> ".json"
            "image" -> {
                val fromMime = when {
                    mimeType.contains("gif") -> "gif"
                    mimeType.contains("png") -> "png"
                    mimeType.contains("webp") -> "webp"
                    mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                    else -> null
                }
                val fromPath = uri.path?.substringAfterLast('.', "")?.lowercase()
                val ext = fromMime ?: fromPath?.takeIf { it in IMAGE_EXTENSIONS }
                ".${ext ?: "img"}"
            }
            else -> ".bin"
        }
    }
}

