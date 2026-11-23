package com.vibepop.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import java.io.File

/**
 * 本地多媒体封面提取：
 * 为「动效工坊」的自定义媒体卡片生成一张静态封面（视频取首帧，图片 / GIF 直接解码）。
 * 失败或文件不存在时返回 null，由调用方回退到默认图标。
 */
object MediaThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(8) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun load(path: String, isVideoFile: Boolean, targetSizePx: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val key = "${file.absolutePath}:${file.lastModified()}:$targetSizePx"
        cache.get(key)?.let { return it }

        val bitmap = try {
            if (isVideoFile) decodeVideoFrame(file, targetSizePx)
            else decodeImage(file, targetSizePx)
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeVideoFrame(file: File, targetSizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            frame?.let { cropToSquare(it, targetSizePx) }
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    private fun decodeImage(file: File, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetSizePx)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return cropToSquare(decoded, targetSizePx)
    }

    private fun calculateSampleSize(width: Int, height: Int, targetSizePx: Int): Int {
        if (targetSizePx <= 0) return 1
        var sample = 1
        val shortest = minOf(width, height)
        while (shortest / (sample * 2) >= targetSizePx) {
            sample *= 2
        }
        return sample
    }

    private fun cropToSquare(source: Bitmap, targetSizePx: Int): Bitmap {
        val side = minOf(source.width, source.height)
        if (side <= 0) return source
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val cropped = if (side == source.width && side == source.height) source
        else Bitmap.createBitmap(source, left, top, side, side)
        return if (targetSizePx <= 0 || cropped.width == targetSizePx) cropped
        else Bitmap.createScaledBitmap(cropped, targetSizePx, targetSizePx, true)
    }
}
