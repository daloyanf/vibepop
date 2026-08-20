package com.vibepop.data.model

/**
 * 弹窗配置偏好
 */
data class PopupConfig(
    val autoDismissSeconds: Int = 4,
    val customDeviceName: String = "AirPods Pro 2",
    val isServiceEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isForceSpeakerphone: Boolean = true,        // 强制手机外置扬声器(喇叭)外放声音
    val animationTheme: String = "classic_airpods", // "classic_airpods", "cyberpunk_mecha", "minimalist_pulse", "custom_media"
    val customMediaPath: String? = null,           // 导入的本地 MP4视频 / GIF / 图片 路径
    val customMediaType: String = "preset",        // "video", "image", "lottie", "preset"
    val videoDismissMode: String = "on_complete",  // "on_complete": 视频播放完毕后消退, "timer": 按设定倒计时消退
    val targetDeviceAddresses: Set<String> = emptySet() // 空代表响应所有蓝牙音频设备，非空代表仅响应白名单
) : java.io.Serializable
