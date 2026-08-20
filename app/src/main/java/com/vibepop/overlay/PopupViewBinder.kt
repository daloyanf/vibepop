package com.vibepop.overlay

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.vibepop.R
import com.vibepop.data.model.DeviceBatteryState
import com.vibepop.data.model.PopupConfig
import java.io.File
import java.io.FileInputStream

/**
 * 负责弹窗视图的数据绑定、全格式多媒体 (MP4视频/GIF/图片/Lottie) 加载与电量渲染
 */
class PopupViewBinder(
    private val rootView: View,
    private val config: PopupConfig,
    private val onDismissRequested: () -> Unit
) {

    private val tvDeviceName: TextView = rootView.findViewById(R.id.tvDeviceName)
    private val tvConnectionStatus: TextView = rootView.findViewById(R.id.tvConnectionStatus)
    private val lottieView: LottieAnimationView = rootView.findViewById(R.id.lottieAnimationView)
    private val videoView: VideoView = rootView.findViewById(R.id.videoView)
    private val ivCustomImage: ImageView = rootView.findViewById(R.id.ivCustomImage)
    private val viewGradientOverlay: View? = rootView.findViewById(R.id.viewGradientOverlay)

    private val tvBatteryLeft: TextView = rootView.findViewById(R.id.tvBatteryLeft)
    private val ivChargingLeft: ImageView = rootView.findViewById(R.id.ivChargingLeft)
    private val tvBatteryRight: TextView = rootView.findViewById(R.id.tvBatteryRight)
    private val ivChargingRight: ImageView = rootView.findViewById(R.id.ivChargingRight)
    private val tvBatteryCase: TextView = rootView.findViewById(R.id.tvBatteryCase)
    private val ivChargingCase: ImageView = rootView.findViewById(R.id.ivChargingCase)

    fun bind(deviceState: DeviceBatteryState) {
        val context = rootView.context

        // 设备名称与连接状态 (左上角展示当前连接的真实蓝牙设备名称)
        val displayName = if (deviceState.deviceName.isNotBlank()) {
            deviceState.deviceName
        } else {
            config.customDeviceName
        }
        tvDeviceName.text = displayName
        tvConnectionStatus.text = context.getString(R.string.status_connected_text)

        // 绑定电量
        bindBatteryItem(tvBatteryLeft, ivChargingLeft, deviceState.leftBattery, deviceState.isLeftCharging)
        bindBatteryItem(tvBatteryRight, ivChargingRight, deviceState.rightBattery, deviceState.isRightCharging)
        bindBatteryItem(tvBatteryCase, ivChargingCase, deviceState.caseBattery, deviceState.isCaseCharging)

        // 动效/多媒体选型与加载
        setupMedia()
    }

    private fun setupMedia() {
        try {
            // 1. 如果用户导入了本地媒体文件 (视频 / 图片 / Lottie)
            if (!config.customMediaPath.isNullOrBlank()) {
                val file = File(config.customMediaPath)
                if (file.exists()) {
                    when (config.customMediaType) {
                        "video" -> {
                            viewGradientOverlay?.visibility = View.GONE
                            playVideo(file)
                            return
                        }
                        "image" -> {
                            viewGradientOverlay?.visibility = View.GONE
                            showImage(file)
                            return
                        }
                        "lottie" -> {
                            viewGradientOverlay?.visibility = View.VISIBLE
                            playLottieFile(file)
                            return
                        }
                    }
                }
            }

            // 2. 内置预设 Lottie 动画加载
            viewGradientOverlay?.visibility = View.VISIBLE
            videoView.visibility = View.GONE
            ivCustomImage.visibility = View.GONE
            lottieView.visibility = View.VISIBLE

            lottieView.repeatCount = LottieDrawable.INFINITE
            val assetName = when (config.animationTheme) {
                "cyberpunk_mecha" -> "cyberpunk_mecha.json"
                "minimalist_pulse" -> "minimalist_pulse.json"
                else -> "headset_animation.json"
            }
            lottieView.setAnimation(assetName)
            lottieView.playAnimation()

        } catch (e: Exception) {
            showFallback()
        }
    }

    private fun playVideo(file: File) {
        lottieView.visibility = View.GONE
        ivCustomImage.visibility = View.GONE
        videoView.visibility = View.VISIBLE

        videoView.setVideoPath(file.absolutePath)
        val isOnComplete = config.videoDismissMode == "on_complete"

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = !isOnComplete // 播放完消退模式下不循环；定时消退模式下循环播放
            mp.setVolume(1.0f, 1.0f)     // 开启视频原生原声

            // 满铺居中裁剪 (Center Crop)，彻底消除黑边与内嵌框感觉
            val videoWidth = mp.videoWidth.toFloat()
            val videoHeight = mp.videoHeight.toFloat()
            if (videoWidth > 0 && videoHeight > 0) {
                val mediaContainer = rootView.findViewById<View>(R.id.mediaContainer)
                mediaContainer?.post {
                    val containerWidth = mediaContainer.width.toFloat()
                    val containerHeight = mediaContainer.height.toFloat()
                    if (containerWidth > 0 && containerHeight > 0) {
                        val scale = maxOf(containerWidth / videoWidth, containerHeight / videoHeight)
                        val targetWidth = (videoWidth * scale).toInt()
                        val targetHeight = (videoHeight * scale).toInt()
                        val lp = FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.CENTER)
                        videoView.layoutParams = lp
                    }
                }
            }

            // 强制手机扬声器 (喇叭) 外放声音
            if (config.isForceSpeakerphone) {
                routeAudioToSpeaker(mp)
            }

            mp.start()
        }

        if (isOnComplete) {
            videoView.setOnCompletionListener {
                onDismissRequested()
            }
        }

        videoView.setOnErrorListener { _, _, _ ->
            showFallback()
            true
        }
        videoView.start()
    }

    private fun routeAudioToSpeaker(mp: MediaPlayer) {
        try {
            val context = rootView.context
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val speakerDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speakerDevice != null) {
                    mp.setPreferredDevice(speakerDevice)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speakerDevice = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speakerDevice != null) {
                    audioManager.setCommunicationDevice(speakerDevice)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun release() {
        try {
            if (videoView.isPlaying) {
                videoView.stopPlayback()
            }

            // 恢复系统默认音频路由
            val context = rootView.context
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun showImage(file: File) {
        lottieView.visibility = View.GONE
        videoView.visibility = View.GONE
        ivCustomImage.visibility = View.VISIBLE

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap != null) {
            ivCustomImage.setImageBitmap(bitmap)
        } else {
            ivCustomImage.setImageURI(Uri.fromFile(file))
        }
    }

    private fun playLottieFile(file: File) {
        videoView.visibility = View.GONE
        ivCustomImage.visibility = View.GONE
        lottieView.visibility = View.VISIBLE

        lottieView.repeatCount = LottieDrawable.INFINITE
        lottieView.setAnimation(FileInputStream(file), null)
        lottieView.playAnimation()
    }

    private fun showFallback() {
        videoView.visibility = View.GONE
        ivCustomImage.visibility = View.GONE
        lottieView.visibility = View.VISIBLE
        try {
            lottieView.setAnimation("headset_animation.json")
            lottieView.playAnimation()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun bindBatteryItem(
        textView: TextView,
        chargingIcon: ImageView,
        batteryPercent: Int,
        isCharging: Boolean
    ) {
        if (batteryPercent < 0) {
            textView.text = "--%"
            chargingIcon.visibility = View.GONE
            return
        }

        textView.text = "$batteryPercent%"
        chargingIcon.visibility = if (isCharging) View.VISIBLE else View.GONE

        // 根据电量动态着色
        val colorRes = when {
            isCharging -> R.color.battery_charging_icon
            batteryPercent <= 20 -> R.color.battery_red
            batteryPercent <= 40 -> R.color.battery_yellow
            else -> R.color.text_primary
        }
        textView.setTextColor(ContextCompat.getColor(rootView.context, colorRes))
    }
}
