package com.vibepop.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹窗配置消退策略辅助方法的单元测试
 */
class PopupConfigTest {

    @Test
    fun `preset video with on-complete mode dismisses after video`() {
        val config = PopupConfig(animationTheme = "great_victory", videoDismissMode = "on_complete")
        assertTrue(config.isVideoDismissOnComplete())
        assertEquals(60_000L, config.resolveDismissDelayMillis())
    }

    @Test
    fun `custom mp4 file is detected by extension`() {
        val config = PopupConfig(
            animationTheme = "custom_media",
            customMediaType = "preset",
            customMediaPath = "/data/user/0/com.vibepop/files/custom_popup_media.mp4"
        )
        assertTrue(config.isVideoDismissOnComplete())
    }

    @Test
    fun `webm file is detected by extension`() {
        val config = PopupConfig(
            animationTheme = "custom_media",
            customMediaType = "preset",
            customMediaPath = "/data/custom_popup_media.webm"
        )
        assertTrue(config.isVideoDismissOnComplete())
    }

    @Test
    fun `timer mode never uses video completion dismiss`() {
        val config = PopupConfig(
            animationTheme = "great_victory",
            videoDismissMode = "timer",
            autoDismissSeconds = 5
        )
        assertFalse(config.isVideoDismissOnComplete())
        assertEquals(5_000L, config.resolveDismissDelayMillis())
    }

    @Test
    fun `non-video theme uses configured seconds`() {
        val config = PopupConfig(autoDismissSeconds = 8)
        assertFalse(config.isVideoDismissOnComplete())
        assertEquals(8_000L, config.resolveDismissDelayMillis())
    }

    @Test
    fun `dismiss delay never below two seconds`() {
        val config = PopupConfig(autoDismissSeconds = 1)
        assertEquals(2_000L, config.resolveDismissDelayMillis())
    }
}