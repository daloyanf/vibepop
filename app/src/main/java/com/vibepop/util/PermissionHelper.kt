package com.vibepop.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * 是否具有悬浮窗权限 (SYSTEM_ALERT_WINDOW)
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 跳转至悬浮窗授权设置页面
     */
    fun requestOverlayPermission(activity: Activity, requestCode: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, requestCode)
        }
    }

    /**
     * 是否具有蓝牙相关权限 (Android 12+ 需要 BLUETOOTH_CONNECT)
     */
    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 手机系统蓝牙是否处于开启状态
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            @Suppress("DEPRECATION")
            val adapter = bluetoothManager?.adapter ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 调起系统原生弹窗快速开启蓝牙或跳转至蓝牙设置
     */
    fun promptEnableBluetooth(activity: Activity) {
        try {
            val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivity(enableBtIntent)
        } catch (e: Exception) {
            try {
                val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                activity.startActivity(settingsIntent)
            } catch (e2: Exception) {
                // ignore
            }
        }
    }

    /**
     * 请求蓝牙运行时权限
     */
    fun requestBluetoothPermission(activity: Activity, requestCode: Int = 1002) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            ActivityCompat.requestPermissions(activity, permissions, requestCode)
        }
    }

    /**
     * 是否具有通知权限 (Android 13+ 需要 POST_NOTIFICATIONS)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 请求通知权限
     */
    fun requestNotificationPermission(activity: Activity, requestCode: Int = 1003) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }

    /**
     * 是否具有相册/多媒体读取权限
     */
    fun hasMediaPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val hasVideos = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            hasImages || hasVideos
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求相册/多媒体读取权限
     */
    fun requestMediaPermission(activity: Activity, requestCode: Int = 1004) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                ),
                requestCode
            )
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                requestCode
            )
        }
    }

    /**
     * 检查小米/HyperOS/华为等厂商的锁屏显示与后台弹出权限状态
     */
    fun hasOemLockScreenPermission(context: Context): Boolean {
        val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
                Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Redmi", ignoreCase = true)

        if (isXiaomi) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return true
            return try {
                val method = appOps.javaClass.getMethod(
                    "checkOpNoThrow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                val opShowWhenLocked = 10020 // MIUI 锁屏显示
                val opBackgroundStart = 10021 // MIUI 后台弹出界面
                val result1 = method.invoke(appOps, opShowWhenLocked, android.os.Process.myUid(), context.packageName) as Int
                val result2 = method.invoke(appOps, opBackgroundStart, android.os.Process.myUid(), context.packageName) as Int
                result1 == android.app.AppOpsManager.MODE_ALLOWED && result2 == android.app.AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) {
                try {
                    val method = appOps.javaClass.getMethod(
                        "checkOpNoThrow",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        String::class.java
                    )
                    val opShowWhenLocked = 10020
                    val result1 = method.invoke(appOps, opShowWhenLocked, android.os.Process.myUid(), context.packageName) as Int
                    result1 == android.app.AppOpsManager.MODE_ALLOWED
                } catch (e2: Exception) {
                    hasOverlayPermission(context)
                }
            }
        }

        // 其他设备，默认跟随悬浮窗权限
        return hasOverlayPermission(context)
    }

    /**
     * 打开系统应用详情或厂商特定权限管理页（支持小米/HyperOS/华为/OPPO/vivo 锁屏显示与后台弹出）
     */
    fun openAppDetailsOrOemSettings(activity: Activity) {
        try {
            // 小米 / MIUI / HyperOS 权限管理页
            val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", activity.packageName)
            }
            activity.startActivity(miuiIntent)
            return
        } catch (e: Exception) {}

        try {
            val detailsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(detailsIntent)
        } catch (e: Exception) {}
    }
}
