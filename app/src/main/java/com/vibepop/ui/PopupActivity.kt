package com.vibepop.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.vibepop.R
import com.vibepop.data.model.DeviceBatteryState
import com.vibepop.data.model.PopupConfig
import com.vibepop.databinding.ActivityPopupBinding
import com.vibepop.overlay.PopupAutoDismissManager
import com.vibepop.overlay.PopupViewBinder
import com.vibepop.overlay.SlideDismissTouchListener

/**
 * 专用于系统锁屏、熄屏或无悬浮窗场景下的穿透式弹窗 Activity
 */
class PopupActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_DEVICE_STATE = "extra_device_state"
        private const val EXTRA_POPUP_CONFIG = "extra_popup_config"

        var isActivityRunning = false
            private set

        fun start(
            context: Context,
            deviceState: DeviceBatteryState = DeviceBatteryState.mock(),
            config: PopupConfig = PopupConfig()
        ) {
            try {
                // 1. 熄屏唤醒：点亮屏幕
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = pm?.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "VibePop:LockScreenWakeLock"
                )
                wakeLock?.acquire(3000)

                // 2. 启动锁屏穿透 Activity
                val intent = Intent(context, PopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_DEVICE_STATE, deviceState)
                    putExtra(EXTRA_POPUP_CONFIG, config)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private lateinit var binding: ActivityPopupBinding
    private var binder: PopupViewBinder? = null
    private var autoDismissManager: PopupAutoDismissManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActivityRunning = true

        // 1. 穿透锁屏与唤醒屏幕标志
        setupLockScreenFlags()

        binding = ActivityPopupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. 提取传递的电量状态与配置
        val deviceState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_DEVICE_STATE, DeviceBatteryState::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_DEVICE_STATE) as? DeviceBatteryState
        } ?: DeviceBatteryState.mock()

        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_POPUP_CONFIG, PopupConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_POPUP_CONFIG) as? PopupConfig
        } ?: PopupConfig()

        // 3. 计算 40% 屏幕高度
        val screenHeight = resources.displayMetrics.heightPixels
        val targetHeight = (screenHeight * 0.40f).toInt()
        val cardView = binding.layoutPopup.cardPopup
        cardView.layoutParams = cardView.layoutParams.apply {
            height = targetHeight
        }

        // 4. 绑定多媒体与电量
        binder = PopupViewBinder(binding.layoutPopup.root, config) {
            dismiss()
        }
        binder?.bind(deviceState)

        // 5. 自动消退倒计时
        val isVideoOnComplete = (config.animationTheme == "great_victory" ||
                config.customMediaType == "video" ||
                config.customMediaPath?.lowercase()?.endsWith(".mp4") == true)
                && config.videoDismissMode == "on_complete"

        val delayMillis = if (isVideoOnComplete) {
            60_000L
        } else {
            (config.autoDismissSeconds * 1000L).coerceAtLeast(2000L)
        }

        autoDismissManager = PopupAutoDismissManager(delayMillis) {
            dismiss()
        }
        autoDismissManager?.start()

        // 6. 手势拖拽下拉关闭
        val touchListener = SlideDismissTouchListener(
            targetView = cardView,
            onDismiss = { dismiss() },
            onTouchStateChanged = { isInteracting ->
                if (isInteracting) autoDismissManager?.pause() else autoDismissManager?.resume()
            }
        )
        cardView.setOnTouchListener(touchListener)

        // 7. 点击外部半透明背景关闭
        binding.popupActivityRoot.setOnClickListener {
            dismiss()
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun dismiss() {
        if (isFinishing || isDestroyed) return
        autoDismissManager?.cancel()
        autoDismissManager = null
        binder?.release()
        binder = null
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, R.anim.slide_down_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityRunning = false
        autoDismissManager?.cancel()
        binder?.release()
    }
}
