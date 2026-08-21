@file:Suppress("DEPRECATION")

package com.vibepop.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Movie
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.min

/**
 * 基于 android.graphics.Movie 的 GIF 帧动画 Drawable。
 * Android 8.0/8.1 (API 26-27) 无 AnimatedImageDrawable，以此兜底；28+ 优先走 ImageDecoder
 */
class GifMovieDrawable(private val movie: Movie) : Drawable() {

    private var startTime = -1L

    override fun draw(canvas: Canvas) {
        val movieWidth = movie.width()
        val movieHeight = movie.height()
        if (movieWidth <= 0 || movieHeight <= 0) return

        val now = SystemClock.uptimeMillis()
        if (startTime < 0) {
            startTime = now
        }
        val duration = movie.duration()
        val relativeTime = if (duration > 0) ((now - startTime) % duration).toInt() else 0
        movie.setTime(relativeTime)

        // 等比缩放居中绘制 (Center Crop 风格)
        val scale = min(
            bounds.width().toFloat() / movieWidth,
            bounds.height().toFloat() / movieHeight
        )
        canvas.save()
        canvas.translate(
            bounds.left + (bounds.width() - movieWidth * scale) / 2f,
            bounds.top + (bounds.height() - movieHeight * scale) / 2f
        )
        canvas.scale(scale, scale)
        movie.draw(canvas, 0f, 0f)
        canvas.restore()

        // 持续刷新下一帧
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        // Movie 不支持透明度设置，忽略
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Movie 不支持颜色滤镜，忽略
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}