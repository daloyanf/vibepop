package com.vibepop.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
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
import com.vibepop.ui.PopupActivity
import com.vibepop.util.PermissionHelper

/**
 * 悬浮窗核心生命周期管理器 (单例模式)
 */
object PopupWindowManager {

    private const val TAG = "PopupWindowManager"

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var popupInteraction: PopupInteractionController? = null
    private var isShowing = false

    private var currentBinder: PopupViewBinder? = null
    private var currentDeviceAddress: String? = null

    /**
     * 显示拟真耳机弹窗
     */
    @Synchronized
    fun showPopup(
        context: Context,
        deviceState: DeviceBatteryState = DeviceBatteryState.mock(),
        config: PopupConfig = PopupConfig()
    ) {
        // 若已有弹窗，先优雅清理（同时联动关闭可能存在的锁屏穿透弹窗）
        dismissPopup(immediate = true)

        // 1. 检查当前是否处于锁屏或熄屏状态
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val isScreenOff = powerManager?.isInteractive == false
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val overlayAllowed = PermissionHelper.hasOverlayPermission(context)

        // 锁屏或熄屏场景：Android 系统层级限制 TYPE_APPLICATION_OVERLAY 悬浮窗无法置于锁屏之上，
        // 必须使用配置了 showWhenLocked 与 turnScreenOn 的 PopupActivity 穿透锁屏呈现弹窗
        if (isScreenOff || isLocked) {
            Log.d(TAG, "Device is locked ($isLocked) or screen is off ($isScreenOff), launching PopupActivity to penetrate lock screen")
            PopupActivity.start(context, deviceState, config)
            return
        }

        if (!overlayAllowed) {
            Log.w(TAG, "Cannot show popup: SYSTEM_ALERT_WINDOW permission missing")
            return
        }

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            // 2. 加载弹窗布局 (使用 ContextThemeWrapper 赋予 MaterialComponents 主题)
            val themedContext = androidx.appcompat.view.ContextThemeWrapper(context, R.style.Theme_VibePop)
            val inflater = LayoutInflater.from(themedContext)
            val root = inflater.inflate(R.layout.layout_headset_popup, null)
            popupView = root

            // 3. 窗口参数配置 (底部吸附 + 进出场动效 + 状态栏/导航栏穿透)
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

            // 4. 动态计算屏幕尺寸，精准设定 40% 弹窗高度
            val cardView = root.findViewById<View>(R.id.cardPopup)
            cardView.layoutParams = cardView.layoutParams.apply {
                height = PopupMetrics.targetPopupHeight(context)
            }

            // 5. 绑定数据与动画
            val binder = PopupViewBinder(root, config) {
                dismissPopup()
            }
            currentBinder = binder
            currentDeviceAddress = deviceState.deviceAddress
            binder.bind(deviceState)

            // 6. 下拉拖拽手势与自动消退统一控制器
            val interaction = PopupInteractionController(
                cardView = cardView,
                onDismiss = { dismissPopup(immediate = true) },
                dismissDelayMillis = config.resolveDismissDelayMillis()
            )
            popupInteraction = interaction
            interaction.attach()

            // 7. 挂载至 Window
            wm.addView(root, params)
            isShowing = true

            // 8. 熄屏状态下短暂点亮屏幕，保证用户看到弹窗
            if (isScreenOff) {
                wakeScreen(context)
            }

            // 9. 启动自动消退倒计时
            interaction.startAutoDismiss()

            // 10. 触觉震动反馈
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
     * 弹窗显示期间收到电量广播时，仅刷新当前设备的电量徽标
     * (不重新加载媒体、不重复触发弹窗，避免耳机周期上报电量时反复打扰)
     */
    fun updateBatteryIfShowing(deviceAddress: String, batteryLevel: Int) {
        if (!isShowing) return
        if (deviceAddress.isBlank()) return
        val current = currentDeviceAddress ?: return
        if (!current.equals(deviceAddress, ignoreCase = true)) return
        try {
            currentBinder?.updateBattery(batteryLevel)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update battery on showing popup: ${e.message}")
        }
    }

    /**
     * 隐藏并移除弹窗
     */
    @Synchronized
    fun dismissPopup(immediate: Boolean = false) {
        // 悬浮窗与锁屏穿透弹窗共用生命周期：任何关闭请求都应联动关闭 PopupActivity
        PopupActivity.dismissIfShowing()

        popupInteraction?.cancel()
        popupInteraction = null

        if (!isShowing && popupView == null) return

        try {
            currentBinder?.release()
            currentBinder = null
            currentDeviceAddress = null

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
     * 熄屏状态下短暂点亮屏幕 (配合 FLAG_SHOW_WHEN_LOCKED 的悬浮窗展示)
     */
    private fun wakeScreen(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            @Suppress("DEPRECATION")
            powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "VibePop:OverlayWakeLock"
            )?.acquire(3000)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to wake screen: ${e.message}")
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