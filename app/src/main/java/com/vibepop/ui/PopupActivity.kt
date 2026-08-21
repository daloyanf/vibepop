package com.vibepop.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.vibepop.R
import com.vibepop.data.model.DeviceBatteryState
import com.vibepop.data.model.PopupConfig
import com.vibepop.databinding.ActivityPopupBinding
import com.vibepop.overlay.PopupInteractionController
import com.vibepop.overlay.PopupMetrics
import com.vibepop.overlay.PopupViewBinder

/**
 * 专用于系统锁屏、熄屏或无悬浮窗权限场景下的穿透式弹窗 Activity
 */
class PopupActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_DEVICE_STATE = "extra_device_state"
        private const val EXTRA_POPUP_CONFIG = "extra_popup_config"

        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var currentInstance: PopupActivity? = null

        /**
         * 锁屏穿透弹窗是否正在展示
         */
        val isActivityRunning: Boolean
            get() = currentInstance != null

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

        /**
         * 联动关闭正在展示的锁屏穿透弹窗 (例如耳机断开或新弹窗替换旧弹窗时)
         */
        fun dismissIfShowing() {
            currentInstance?.let { instance ->
                mainHandler.post { instance.dismiss() }
            }
        }

        /**
         * 锁屏穿透弹窗展示期间刷新对应耳机的电量徽标
         */
        fun updateBatteryIfShowing(deviceAddress: String, batteryLevel: Int) {
            currentInstance?.let { instance ->
                mainHandler.post { instance.updateBattery(deviceAddress, batteryLevel) }
            }
        }
    }

    private lateinit var binding: ActivityPopupBinding
    private var binder: PopupViewBinder? = null
    private var interaction: PopupInteractionController? = null
    private var currentDeviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

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
        currentDeviceAddress = deviceState.deviceAddress

        // 3. 计算 40% 屏幕高度
        val cardView = binding.layoutPopup.cardPopup
        cardView.layoutParams = cardView.layoutParams.apply {
            height = PopupMetrics.targetPopupHeight(this@PopupActivity)
        }

        // 4. 绑定多媒体与电量
        binder = PopupViewBinder(binding.layoutPopup.root, config) {
            dismiss()
        }
        binder?.bind(deviceState)

        // 5. 下拉手势与自动消退统一控制器
        interaction = PopupInteractionController(
            cardView = cardView,
            onDismiss = { dismiss() },
            dismissDelayMillis = config.resolveDismissDelayMillis()
        )
        interaction?.attach()
        interaction?.startAutoDismiss()

        // 6. 点击外部半透明背景关闭
        binding.popupActivityRoot.setOnClickListener {
            dismiss()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

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
        currentDeviceAddress = deviceState.deviceAddress

        val cardView = binding.layoutPopup.cardPopup
        cardView.layoutParams = cardView.layoutParams.apply {
            height = PopupMetrics.targetPopupHeight(this@PopupActivity)
        }

        binder?.release()
        binder = PopupViewBinder(binding.layoutPopup.root, config) {
            dismiss()
        }
        binder?.bind(deviceState)

        interaction?.cancel()
        interaction = PopupInteractionController(
            cardView = cardView,
            onDismiss = { dismiss() },
            dismissDelayMillis = config.resolveDismissDelayMillis()
        )
        interaction?.attach()
        interaction?.startAutoDismiss()
    }

    /**
     * 仅刷新与当前展示设备一致的弹窗电量徽标
     */
    private fun updateBattery(deviceAddress: String, batteryLevel: Int) {
        val current = currentDeviceAddress ?: return
        if (current.isBlank() || !current.equals(deviceAddress, ignoreCase = true)) return
        binder?.updateBattery(batteryLevel)
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun dismiss() {
        if (isFinishing || isDestroyed) return
        interaction?.cancel()
        interaction = null
        binder?.release()
        binder = null
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, R.anim.slide_down_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance === this) {
            currentInstance = null
        }
        interaction?.cancel()
        binder?.release()
    }
}