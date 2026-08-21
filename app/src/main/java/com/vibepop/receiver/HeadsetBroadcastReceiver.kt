package com.vibepop.receiver

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.vibepop.R
import com.vibepop.data.model.DeviceBatteryState
import com.vibepop.data.repository.PreferencesRepository
import com.vibepop.overlay.PopupWindowManager

/**
 * 蓝牙广播接收器：监听耳机连接事件与真实电量更新并触发拟真弹窗
 */
class HeadsetBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeadsetReceiver"
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val fallbackName = context.getString(R.string.device_name_fallback)
        val deviceName = try { device?.name ?: fallbackName } catch (e: Exception) { fallbackName }
        val deviceAddress = try { device?.address ?: "" } catch (e: Exception) { "" }
        Log.d(TAG, "Received broadcast action: $action, device: $deviceName ($deviceAddress)")

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                handleDeviceConnected(context, intent, device)
            }

            ACTION_BATTERY_LEVEL_CHANGED -> {
                handleBatteryLevelChanged(intent, device)
            }

            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                Log.d(TAG, "Profile state changed: $state")
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    handleDeviceConnected(context, intent, device)
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    PopupWindowManager.dismissPopup()
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                PopupWindowManager.dismissPopup()
            }
        }
    }

    /**
     * 设备已连接期间周期上报电量：仅刷新正在显示的弹窗电量徽标，
     * 不重新弹出弹窗，避免用户已连接时反复被打扰
     */
    private fun handleBatteryLevelChanged(intent: Intent, device: BluetoothDevice?) {
        val deviceAddress = try { device?.address ?: "" } catch (e: Exception) { "" }
        if (deviceAddress.isBlank()) {
            Log.d(TAG, "Battery level changed but device address missing, ignoring")
            return
        }
        val level = getRealBatteryLevel(intent, device)
        Log.d(TAG, "Battery level updated: $deviceAddress -> $level")
        PopupWindowManager.updateBatteryIfShowing(deviceAddress, level)
    }

    private fun handleDeviceConnected(context: Context, intent: Intent, device: BluetoothDevice?) {
        // 防抖检查 (3秒)
        if (!PopupTriggerLimiter.canTrigger()) {
            Log.d(TAG, "Debounce limit reached, skipping popup trigger")
            return
        }

        val prefsRepo = PreferencesRepository(context)
        val globalConfig = prefsRepo.getPopupConfig()

        var rawDeviceName = context.getString(R.string.default_device_name)
        var deviceAddress = ""

        try {
            if (device != null) {
                deviceAddress = device.address ?: ""
                val rawName = device.name
                if (!rawName.isNullOrBlank()) {
                    rawDeviceName = rawName
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading device info (permission missing): ${e.message}")
        }

        Log.d(TAG, "Checking device: $rawDeviceName, address: $deviceAddress vs targets: ${globalConfig.targetDeviceAddresses}")

        // 耳机过滤策略：默认全不响应。只有用户在白名单中主动勾选了该设备，才响应弹窗
        if (globalConfig.targetDeviceAddresses.isEmpty()) {
            Log.d(TAG, "No target devices configured in whitelist (default ignore all), skipping popup trigger")
            return
        }

        val isMatched = globalConfig.targetDeviceAddresses.any { target ->
            target.equals(deviceAddress, ignoreCase = true) ||
            (deviceAddress.isNotBlank() && target.contains(deviceAddress, ignoreCase = true)) ||
            (rawDeviceName.isNotBlank() && target.contains(rawDeviceName, ignoreCase = true))
        }
        if (!isMatched) {
            Log.d(TAG, "Device $deviceAddress ($rawDeviceName) is not in target whitelist ${globalConfig.targetDeviceAddresses}, ignoring")
            return
        }

        // 读取该耳机的专属独立弹窗配置
        val deviceConfig = prefsRepo.getDevicePopupConfig(deviceAddress)
        val popupDeviceName = if (deviceConfig.customDeviceName.isNotBlank()) deviceConfig.customDeviceName else rawDeviceName

        Log.d(TAG, "Triggering popup for connected headset: $popupDeviceName ($deviceAddress), theme: ${deviceConfig.animationTheme}")

        // 读取硬件上报的真实电量
        val realBattery = getRealBatteryLevel(intent, device)

        val batteryState = DeviceBatteryState(
            deviceName = popupDeviceName,
            deviceAddress = deviceAddress,
            isConnected = true,
            batteryLevel = realBattery,
            isCharging = false
        )

        PopupWindowManager.showPopup(context.applicationContext, batteryState, deviceConfig)
    }

    private fun getRealBatteryLevel(intent: Intent, device: BluetoothDevice?): Int {
        // 1. 尝试从广播 Intent Extra 中提取系统/驱动上报的电量
        val levelFromIntent = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
        if (levelFromIntent in 0..100) {
            return levelFromIntent
        }

        if (device != null) {
            // 2. 通过反射调用底层 BluetoothDevice.getBatteryLevel() 硬件上报接口
            try {
                val method = device.javaClass.getMethod("getBatteryLevel")
                val level = method.invoke(device) as? Int
                if (level != null && level in 0..100) return level
            } catch (e: Exception) {
                // 忽略底层反射异常或不支持电量上报的设备
            }
        }
        return -1
    }
}

