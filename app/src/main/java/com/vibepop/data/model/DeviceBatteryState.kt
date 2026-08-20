package com.vibepop.data.model

/**
 * 蓝牙耳机左右耳及充电盒电量与状态模型
 */
data class DeviceBatteryState(
    val deviceName: String = "AirPods Pro 2",
    val deviceAddress: String = "",
    val isConnected: Boolean = true,
    val leftBattery: Int = 95,          // 0~100, -1 代表未知
    val isLeftCharging: Boolean = false,
    val rightBattery: Int = 95,         // 0~100, -1 代表未知
    val isRightCharging: Boolean = false,
    val caseBattery: Int = 80,          // 0~100, -1 代表未知
    val isCaseCharging: Boolean = true
) : java.io.Serializable {
    companion object {
        fun mock(): DeviceBatteryState = DeviceBatteryState(
            deviceName = "AirPods Pro 2",
            deviceAddress = "00:11:22:33:44:55",
            isConnected = true,
            leftBattery = 95,
            isLeftCharging = false,
            rightBattery = 95,
            isRightCharging = false,
            caseBattery = 85,
            isCaseCharging = true
        )
    }
}
