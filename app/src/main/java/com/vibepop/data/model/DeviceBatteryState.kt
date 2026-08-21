package com.vibepop.data.model

/**
 * 蓝牙耳机真实电量与连接状态模型
 */
data class DeviceBatteryState(
    val deviceName: String = "自定义名称",
    val deviceAddress: String = "",
    val isConnected: Boolean = true,
    val batteryLevel: Int = -1,          // 0~100, -1 代表未知或未上报
    val isCharging: Boolean = false
) : java.io.Serializable {
    companion object {
        fun mock(): DeviceBatteryState = DeviceBatteryState(
            deviceName = "自定义名称",
            deviceAddress = "00:11:22:33:44:55",
            isConnected = true,
            batteryLevel = 85,
            isCharging = false
        )
    }
}
