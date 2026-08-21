package com.vibepop.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * 视频黑边智能检测工具：
 * 在相册导入视频时在后台单次提取首帧缩略图，扫描上下左右黑边区域，
 * 返回有效画面的归一化比例矩形 RectF(leftRatio, topRatio, rightRatio, bottomRatio)。
 * 如果没有黑边，返回标准的 RectF(0f, 0f, 1f, 1f)。
 */
object VideoCropDetector {

    private const val TAG = "VideoCropDetector"

    // 亮度阈值：低于此值（0-255）判定为黑边像素（考虑到微弱噪点/压缩失真，设为 18）
    private const val BLACK_LUMINANCE_THRESHOLD = 18

    // 样本缩放尺寸（160x90 即可在 <2ms 内完成整张图遍历）
    private const val SAMPLE_WIDTH = 160
    private const val SAMPLE_HEIGHT = 90

    fun detectCropRatio(context: Context, uri: Uri): RectF {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            detectFromRetriever(retriever)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect crop from uri: ${e.message}")
            RectF(0f, 0f, 1f, 1f)
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    fun detectCropRatio(file: File): RectF {
        if (!file.exists()) return RectF(0f, 0f, 1f, 1f)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            detectFromRetriever(retriever)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect crop from file: ${e.message}")
            RectF(0f, 0f, 1f, 1f)
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    private fun detectFromRetriever(retriever: MediaMetadataRetriever): RectF {
        val rawBitmap = try {
            retriever.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (e: Exception) {
            null
        } ?: return RectF(0f, 0f, 1f, 1f)

        val sample = try {
            if (rawBitmap.width != SAMPLE_WIDTH || rawBitmap.height != SAMPLE_HEIGHT) {
                Bitmap.createScaledBitmap(rawBitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            rawBitmap
        }

        val w = sample.width
        val h = sample.height
        if (w <= 0 || h <= 0) return RectF(0f, 0f, 1f, 1f)

        // 提取像素数据
        val pixels = IntArray(w * h)
        sample.getPixels(pixels, 0, w, 0, 0, w, h)
        if (sample !== rawBitmap) {
            sample.recycle()
        }
        rawBitmap.recycle()

        // 1. 扫描左边缘
        var left = 0
        for (x in 0 until w / 3) {
            if (isColumnNonBlack(pixels, w, h, x)) {
                left = x
                break
            }
        }

        // 2. 扫描右边缘
        var right = w - 1
        for (x in w - 1 downTo (w * 2 / 3)) {
            if (isColumnNonBlack(pixels, w, h, x)) {
                right = x
                break
            }
        }

        // 3. 扫描顶边缘
        var top = 0
        for (y in 0 until h / 3) {
            if (isRowNonBlack(pixels, w, y)) {
                top = y
                break
            }
        }

        // 4. 扫描底边缘
        var bottom = h - 1
        for (y in h - 1 downTo (h * 2 / 3)) {
            if (isRowNonBlack(pixels, w, y)) {
                bottom = y
                break
            }
        }

        val leftRatio = (left.toFloat() / w).coerceIn(0f, 0.4f)
        val rightRatio = ((right + 1).toFloat() / w).coerceIn(0.6f, 1f)
        val topRatio = (top.toFloat() / h).coerceIn(0f, 0.4f)
        val bottomRatio = ((bottom + 1).toFloat() / h).coerceIn(0.6f, 1f)

        // 如果裁切过小（例如只有 2% 边缘像素误判），视为无黑边
        val finalLeft = if (leftRatio < 0.02f) 0f else leftRatio
        val finalRight = if (rightRatio > 0.98f) 1f else rightRatio
        val finalTop = if (topRatio < 0.02f) 0f else topRatio
        val finalBottom = if (bottomRatio > 0.98f) 1f else bottomRatio

        return RectF(finalLeft, finalTop, finalRight, finalBottom)
    }

    private fun isColumnNonBlack(pixels: IntArray, w: Int, h: Int, x: Int): Boolean {
        var nonBlackCount = 0
        // 跳过上下 10% 避免角标/水印干扰
        val startY = (h * 0.1f).toInt()
        val endY = (h * 0.9f).toInt()
        val total = endY - startY
        for (y in startY until endY) {
            val color = pixels[y * w + x]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            if (luminance > BLACK_LUMINANCE_THRESHOLD) {
                nonBlackCount++
            }
        }
        // 如果一列中有超过 15% 的像素非黑，则认定该列属于画面内容
        return nonBlackCount.toFloat() / total > 0.15f
    }

    private fun isRowNonBlack(pixels: IntArray, w: Int, y: Int): Boolean {
        var nonBlackCount = 0
        val startX = (w * 0.1f).toInt()
        val endX = (w * 0.9f).toInt()
        val total = endX - startX
        for (x in startX until endX) {
            val color = pixels[y * w + x]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            if (luminance > BLACK_LUMINANCE_THRESHOLD) {
                nonBlackCount++
            }
        }
        return nonBlackCount.toFloat() / total > 0.15f
    }
}
