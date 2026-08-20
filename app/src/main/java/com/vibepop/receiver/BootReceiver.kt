package com.vibepop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vibepop.data.repository.PreferencesRepository
import com.vibepop.service.HeadsetMonitorService

/**
 * 开机自启监听
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesRepository(context)
            if (prefs.getPopupConfig().isServiceEnabled) {
                HeadsetMonitorService.start(context)
            }
        }
    }
}
