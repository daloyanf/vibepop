package com.vibepop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 媒体导入类型识别的单元测试：
 * 仅 MP4/WebM 视为视频，MKV 等 MediaPlayer 不可播放的容器应被拒绝导入
 */
class MediaTypeDetectionTest {

    @Test
    fun `mp4 mime is detected as video`() {
        assertEquals("video", detectMediaType("video/mp4", null))
    }

    @Test
    fun `webm extension is detected as video`() {
        assertEquals("video", detectMediaType("", "/storage/clip.webm"))
    }

    @Test
    fun `mkv is rejected as unsupported`() {
        assertEquals("unsupported", detectMediaType("video/x-matroska", "clip.mkv"))
    }

    @Test
    fun `avi and mov are rejected as unsupported`() {
        assertEquals("unsupported", detectMediaType("video/x-msvideo", "clip.avi"))
        assertEquals("unsupported", detectMediaType("video/quicktime", "clip.mov"))
    }

    @Test
    fun `gif is detected as image`() {
        assertEquals("image", detectMediaType("image/gif", "/x/a.gif"))
    }

    @Test
    fun `png and webp are detected as image`() {
        assertEquals("image", detectMediaType("image/png", "photo.png"))
        assertEquals("image", detectMediaType("image/webp", "photo.webp"))
    }

    @Test
    fun `json is detected as lottie`() {
        assertEquals("lottie", detectMediaType("application/json", "anim.json"))
    }

    @Test
    fun `unknown content is unsupported`() {
        assertEquals("unsupported", detectMediaType("application/octet-stream", "file.xyz"))
    }
}