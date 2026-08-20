package com.vibepop.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max

/**
 * 支持下拉拖拽与弹性回弹/快速下甩消除的手势监听器
 */
class SlideDismissTouchListener(
    private val targetView: View,
    private val onDismiss: () -> Unit,
    private val onTouchStateChanged: (isInteracting: Boolean) -> Unit
) : View.OnTouchListener {

    private var initialTouchY = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(targetView.context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(targetView.context).scaledMinimumFlingVelocity * 3

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchY = event.rawY
                isDragging = false
                onTouchStateChanged(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - initialTouchY
                if (!isDragging && deltaY > touchSlop) {
                    isDragging = true
                }

                if (isDragging) {
                    if (deltaY > 0) {
                        // 向下拖动，线性位移
                        targetView.translationY = deltaY
                        // 随着下滑轻微渐隐
                        val progress = 1f - (deltaY / (targetView.height * 1.2f)).coerceIn(0f, 0.7f)
                        targetView.alpha = progress
                    } else {
                        // 向上拖动做阻尼效果
                        val dampedDelta = -((-deltaY).coerceAtMost(60f) * 0.3f)
                        targetView.translationY = dampedDelta
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onTouchStateChanged(false)
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val yVelocity = velocityTracker?.yVelocity ?: 0f
                    val currentTranslationY = targetView.translationY
                    val dismissThreshold = targetView.height * 0.35f

                    val shouldDismiss = (currentTranslationY > dismissThreshold) || (yVelocity > minFlingVelocity)

                    if (shouldDismiss && currentTranslationY > 0) {
                        animateDismiss(yVelocity)
                    } else {
                        animateBounceBack()
                    }
                    recycleVelocityTracker()
                    return true
                } else {
                    // 没有拖动，属于纯点击事件，执行普通点击响应（例如点击内部按钮）
                    recycleVelocityTracker()
                    v.performClick()
                }
            }
        }
        return false
    }

    /**
     * 向下滑动消除动效
     */
    private fun animateDismiss(velocity: Float) {
        val remainingDistance = targetView.height - targetView.translationY
        val duration = (remainingDistance / max(abs(velocity), 1000f) * 1000).toLong().coerceIn(120L, 260L)

        targetView.animate()
            .translationY(targetView.height.toFloat() + 100f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onDismiss()
                }
            })
            .start()
    }

    /**
     * 未达阈值时回弹动效 (Overshoot 拟真弹簧质感)
     */
    private fun animateBounceBack() {
        ObjectAnimator.ofFloat(targetView, View.TRANSLATION_Y, 0f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
        ObjectAnimator.ofFloat(targetView, View.ALPHA, 1f).apply {
            duration = 200
            start()
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
