package com.vibepop

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.vibepop.data.repository.PreferencesRepository
import com.vibepop.service.HeadsetMonitorService

class VibePopApp : Application() {

    companion object {
        const val CHANNEL_ID_SERVICE = "vibepop_headset_monitor_channel"
        const val CHANNEL_NAME_SERVICE = "耳机连接监听服务"
        lateinit var instance: VibePopApp
            private set
    }

    override fun onCreate() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate()
        instance = this
        createNotificationChannel()

        // 默认若开启服务则自动拉起前台保活监听
        val config = PreferencesRepository(this).getPopupConfig()
        if (config.isServiceEnabled) {
            try {
                HeadsetMonitorService.start(this)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                CHANNEL_NAME_SERVICE,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 VibePop 后台耳机弹窗监听服务常驻"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
