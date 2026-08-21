package com.vibepop.data.model

/**
 * 弹窗配置偏好
 */
data class PopupConfig(
    val autoDismissSeconds: Int = 4,
    val customDeviceName: String = "",
    val isServiceEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isForceSpeakerphone: Boolean = true,        // 强制手机外置扬声器(喇叭)外放声音
    val animationTheme: String = "classic_airpods", // "classic_airpods", "great_victory", "custom_media"
    val customMediaPath: String? = null,           // 导入的本地 MP4视频 / GIF / 图片 路径
    val customMediaType: String = "preset",        // "video", "image", "lottie", "preset"
    val videoDismissMode: String = "on_complete",  // "on_complete": 视频播放完毕后消退, "timer": 按设定倒计时消退
    val targetDeviceAddresses: Set<String> = emptySet(), // 目标白名单耳机 MAC 地址列表
    val cropLeft: Float = 0f,                      // 智能去黑边裁切区域 (0..1)
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
) : java.io.Serializable {

    /**
     * 是否为"视频播放完毕后自动消退"模式
     */
    fun isVideoDismissOnComplete(): Boolean {
        val isVideoTheme = animationTheme == "great_victory" ||
                customMediaType == "video" ||
                customMediaPath?.lowercase()?.let {
                    it.endsWith(".mp4") || it.endsWith(".webm")
                } == true
        return isVideoTheme && videoDismissMode == "on_complete"
    }

    /**
     * 计算弹窗自动消退时长：
     * 视频播放完消退模式以 60 秒作为防卡死兜底，其余按用户设定倒计时（下限 2 秒）
     */
    fun resolveDismissDelayMillis(): Long {
        return if (isVideoDismissOnComplete()) {
            60_000L
        } else {
            (autoDismissSeconds * 1000L).coerceAtLeast(2000L)
        }
    }
}
