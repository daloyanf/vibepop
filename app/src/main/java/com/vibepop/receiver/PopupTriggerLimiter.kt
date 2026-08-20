package com.vibepop.receiver

/**
 * 弹窗触发节流与防抖限制器
 * 防止蓝牙握手时广播抖动导致短时间内连续唤起多次弹窗
 */
object PopupTriggerLimiter {

    private var lastTriggerTime = 0L
    private const val DEFAULT_DEBOUNCE_INTERVAL = 3_000L // 3秒内防抖

    @Synchronized
    fun canTrigger(intervalMillis: Long = DEFAULT_DEBOUNCE_INTERVAL): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime > intervalMillis) {
            lastTriggerTime = now
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        lastTriggerTime = 0L
    }
}
