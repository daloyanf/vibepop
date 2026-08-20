package com.vibepop.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.vibepop.R
import com.vibepop.data.model.DeviceBatteryState
import com.vibepop.data.model.PopupConfig
import com.vibepop.util.PermissionHelper

/**
 * 悬浮窗核心生命周期管理器 (单例模式)
 */
object PopupWindowManager {

    private const val TAG = "PopupWindowManager"

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var autoDismissManager: PopupAutoDismissManager? = null
    private var isShowing = false

    private var currentBinder: PopupViewBinder? = null

    /**
     * 显示拟真耳机弹窗
     */
    @Synchronized
    fun showPopup(
        context: Context,
        deviceState: DeviceBatteryState = DeviceBatteryState.mock(),
        config: PopupConfig = PopupConfig()
    ) {
        // 若已有弹窗，先优雅清理
        dismissPopup(immediate = true)

        // 1. 检查当前是否处于锁屏或熄屏状态
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val isScreenOff = powerManager?.isInteractive == false
        val isLocked = keyguardManager?.isKeyguardLocked == true

        if (isScreenOff || isLocked) {
            Log.d(TAG, "Device is locked ($isLocked) or screen is off ($isScreenOff), launching PopupActivity")
            com.vibepop.ui.PopupActivity.start(context, deviceState, config)
            return
        }

        if (!PermissionHelper.hasOverlayPermission(context)) {
            Log.w(TAG, "Cannot show popup: SYSTEM_ALERT_WINDOW permission missing")
            return
        }

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            // 1. 加载弹窗布局 (使用 ContextThemeWrapper 赋予 MaterialComponents 主题)
            val themedContext = androidx.appcompat.view.ContextThemeWrapper(context, R.style.Theme_VibePop)
            val inflater = LayoutInflater.from(themedContext)
            val root = inflater.inflate(R.layout.layout_headset_popup, null)
            popupView = root

            // 2. 窗口参数配置 (底部吸附 + 进出场动效 + 状态栏/导航栏穿透)
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            @Suppress("DEPRECATION")
            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                windowAnimations = R.style.PopupAnimation
                dimAmount = 0.25f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    blurBehindRadius = 80
                }
            }

            // 3. 动态计算屏幕尺寸，精准设定 40% 弹窗高度
            val displayMetrics = context.resources.displayMetrics
            val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.currentWindowMetrics.bounds.height()
            } else {
                displayMetrics.heightPixels
            }
            val targetPopupHeight = (screenHeight * 0.40f).toInt()
            val cardView = root.findViewById<View>(R.id.cardPopup)
            cardView.layoutParams = cardView.layoutParams.apply {
                height = targetPopupHeight
            }

            // 4. 绑定数据与动画
            val binder = PopupViewBinder(root, config) {
                dismissPopup()
            }
            currentBinder = binder
            binder.bind(deviceState)

            // 4. 自动消退调度器
            val isVideoOnComplete = (config.customMediaType == "video" || config.customMediaPath?.lowercase()?.endsWith(".mp4") == true)
                    && config.videoDismissMode == "on_complete"

            val delayMillis = if (isVideoOnComplete) {
                60_000L // 视频播放完自动消退模式：60秒作为防卡死安全保底
            } else {
                (config.autoDismissSeconds * 1000L).coerceAtLeast(2000L)
            }

            autoDismissManager = PopupAutoDismissManager(delayMillis) {
                dismissPopup()
            }

            // 5. 下拉拖拽手势绑定
            val touchListener = SlideDismissTouchListener(
                targetView = cardView,
                onDismiss = { dismissPopup(immediate = true) },
                onTouchStateChanged = { isInteracting ->
                    if (isInteracting) {
                        autoDismissManager?.pause()
                    } else {
                        autoDismissManager?.resume()
                    }
                }
            )
            cardView.setOnTouchListener(touchListener)

            // 6. 挂载至 Window
            wm.addView(root, params)
            isShowing = true

            // 7. 启动自动消退倒计时
            autoDismissManager?.start()

            // 8. 触觉震动反馈
            if (config.isVibrationEnabled) {
                triggerHapticFeedback(context)
            }

            Log.d(TAG, "Popup window presented successfully for: ${deviceState.deviceName}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show popup window", e)
            dismissPopup(immediate = true)
        }
    }

    /**
     * 隐藏并移除弹窗
     */
    @Synchronized
    fun dismissPopup(immediate: Boolean = false) {
        if (!isShowing && popupView == null) return

        try {
            autoDismissManager?.cancel()
            autoDismissManager = null

            currentBinder?.release()
            currentBinder = null

            popupView?.let { view ->
                if (view.isAttachedToWindow) {
                    if (immediate) {
                        windowManager?.removeViewImmediate(view)
                    } else {
                        windowManager?.removeView(view)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while dismissing popup", e)
        } finally {
            popupView = null
            isShowing = false
        }
    }

    /**
     * 触觉轻微震动反馈 (拟真 iOS 触感)
     */
    private fun triggerHapticFeedback(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(30)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic feedback unavailable: ${e.message}")
        }
    }
}
