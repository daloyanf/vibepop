package com.vibepop.overlay

import android.content.Context

/**
 * 弹窗卡片尺寸计算工具（悬浮窗与锁屏 Activity 共用）
 */
object PopupMetrics {

    /**
     * 弹窗卡片目标高度：占屏幕高度 40%
     */
    fun targetPopupHeight(context: Context): Int {
        return (context.resources.displayMetrics.heightPixels * 0.40f).toInt()
    }
}