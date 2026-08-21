package com.vibepop.overlay

import android.view.View

/**
 * 弹窗交互统一控制器：负责下拉手势与自动消退调度，
 * 供悬浮窗 (PopupWindowManager) 与锁屏穿透弹窗 (PopupActivity) 复用，避免逻辑分叉
 */
class PopupInteractionController(
    private val cardView: View,
    private val onDismiss: () -> Unit,
    dismissDelayMillis: Long
) {

    private val autoDismissManager = PopupAutoDismissManager(dismissDelayMillis) { onDismiss() }

    private val touchListener = SlideDismissTouchListener(
        targetView = cardView,
        onDismiss = { onDismiss() },
        onTouchStateChanged = { isInteracting ->
            if (isInteracting) {
                autoDismissManager.pause()
            } else {
                autoDismissManager.resume()
            }
        }
    )

    /**
     * 绑定下拉手势监听
     */
    fun attach() {
        cardView.setOnTouchListener(touchListener)
    }

    /**
     * 启动自动消退倒计时（须在弹窗录入窗口后调用）
     */
    fun startAutoDismiss() {
        autoDismissManager.start()
    }

    /**
     * 取消自动消退调度（弹窗关闭时调用）
     */
    fun cancel() {
        autoDismissManager.cancel()
    }
}