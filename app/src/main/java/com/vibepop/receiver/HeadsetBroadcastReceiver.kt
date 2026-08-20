package com.vibepop.receiver

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vibepop.data.model.DeviceBatteryState
import com.vibepop.data.repository.PreferencesRepository
import com.vibepop.overlay.PopupWindowManager
import com.vibepop.service.HeadsetMonitorService

/**
 * 蓝牙广播接收器：监听耳机连接事件并触发拟真弹窗
 */
class HeadsetBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HeadsetReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val deviceName = try { device?.name ?: "未知耳机" } catch (e: Exception) { "未知耳机" }
        val deviceAddress = try { device?.address ?: "" } catch (e: Exception) { "" }
        Log.d(TAG, "Received broadcast action: $action, device: $deviceName ($deviceAddress)")

        val prefsRepo = PreferencesRepository(context)
        val config = prefsRepo.getPopupConfig()

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                handleDeviceConnected(context, device)
            }

            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                Log.d(TAG, "Profile state changed: $state")
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    handleDeviceConnected(context, device)
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    PopupWindowManager.dismissPopup()
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                PopupWindowManager.dismissPopup()
            }
        }
    }

    private fun handleDeviceConnected(context: Context, device: BluetoothDevice?) {
        // 防抖检查 (3秒)
        if (!PopupTriggerLimiter.canTrigger()) {
            Log.d(TAG, "Debounce limit reached, skipping popup trigger")
            return
        }

        val prefsRepo = PreferencesRepository(context)
        val config = prefsRepo.getPopupConfig()

        var deviceName = config.customDeviceName
        var deviceAddress = ""

        try {
            if (device != null) {
                deviceAddress = device.address ?: ""
                val rawName = device.name
                if (!rawName.isNullOrBlank()) {
                    deviceName = rawName
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading device info (permission missing): ${e.message}")
        }

        Log.d(TAG, "Checking device: $deviceName, address: $deviceAddress vs targets: ${config.targetDeviceAddresses}")

        // 白名单设备过滤：若设置了目标设备白名单且当前设备不在其中，则忽略
        if (config.targetDeviceAddresses.isNotEmpty()) {
            val isMatched = config.targetDeviceAddresses.any { target ->
                target.equals(deviceAddress, ignoreCase = true) ||
                (deviceAddress.isNotBlank() && target.contains(deviceAddress, ignoreCase = true)) ||
                (deviceName.isNotBlank() && target.contains(deviceName, ignoreCase = true))
            }
            if (!isMatched) {
                Log.d(TAG, "Device $deviceAddress ($deviceName) is not in target whitelist ${config.targetDeviceAddresses}, ignoring")
                return
            }
        }

        Log.d(TAG, "Triggering popup for connected headset: $deviceName ($deviceAddress)")

        // 构建电量数据（可从系统私有广播或默认模拟）
        val batteryState = DeviceBatteryState(
            deviceName = deviceName,
            deviceAddress = deviceAddress,
            isConnected = true,
            leftBattery = 95,
            isLeftCharging = false,
            rightBattery = 95,
            isRightCharging = false,
            caseBattery = 85,
            isCaseCharging = true
        )

        PopupWindowManager.showPopup(context.applicationContext, batteryState, config)
    }
}
