package com.vibepop.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vibepop.R
import com.vibepop.VibePopApp
import com.vibepop.receiver.HeadsetBroadcastReceiver
import com.vibepop.ui.MainActivity
import com.vibepop.util.PermissionHelper

/**
 * 蓝牙耳机后台常驻监听服务 (前台服务，保持广播接收器活跃)
 */
class HeadsetMonitorService : Service() {

    companion object {
        private const val TAG = "HeadsetMonitorService"
        private const val NOTIFICATION_ID = 10001
        var isServiceRunning = false
            private set

        fun start(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !PermissionHelper.hasBluetoothPermission(context)) {
                    Log.w(TAG, "Bluetooth permission not granted yet, skip starting FGS")
                    return
                }
                val intent = Intent(context, HeadsetMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start HeadsetMonitorService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, HeadsetMonitorService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop HeadsetMonitorService: ${e.message}")
            }
        }
    }

    private var headsetReceiver: HeadsetBroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeadsetMonitorService onCreate")
        startAsForeground()
        registerHeadsetReceiver()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HeadsetMonitorService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HeadsetMonitorService onDestroy")
        unregisterHeadsetReceiver()
        isServiceRunning = false
    }

    private fun startAsForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, VibePopApp.CHANNEL_ID_SERVICE)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_desc))
            .setSmallIcon(R.drawable.ic_bluetooth_headset)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (PermissionHelper.hasBluetoothPermission(this)) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(NOTIFICATION_ID, notification, 0)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startForeground with type: ${e.message}, falling back to basic notification")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ignored: Exception) {
                stopSelf()
            }
        }
    }

    private fun registerHeadsetReceiver() {
        if (headsetReceiver == null) {
            headsetReceiver = HeadsetBroadcastReceiver()
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(HeadsetBroadcastReceiver.ACTION_BATTERY_LEVEL_CHANGED)
            }
            registerReceiver(headsetReceiver, filter)
            Log.d(TAG, "HeadsetBroadcastReceiver registered dynamically")
        }
    }

    private fun unregisterHeadsetReceiver() {
        headsetReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "HeadsetBroadcastReceiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            headsetReceiver = null
        }
    }
}
