package com.vibepop.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 弹窗触发节流与防抖控制器的单元测试
 */
class PopupTriggerLimiterTest {

    @Before
    fun setUp() {
        PopupTriggerLimiter.reset()
    }

    @Test
    fun `first trigger is allowed`() {
        assertTrue(PopupTriggerLimiter.canTrigger(intervalMillis = 200))
    }

    @Test
    fun `rapid triggers within window are debounced`() {
        assertTrue(PopupTriggerLimiter.canTrigger(intervalMillis = 200))
        assertFalse(PopupTriggerLimiter.canTrigger(intervalMillis = 200))
        assertFalse(PopupTriggerLimiter.canTrigger(intervalMillis = 200))
    }

    @Test
    fun `trigger is allowed again after debounce window`() {
        assertTrue(PopupTriggerLimiter.canTrigger(intervalMillis = 100))
        Thread.sleep(200)
        assertTrue(PopupTriggerLimiter.canTrigger(intervalMillis = 100))
    }

    @Test
    fun `reset clears debounce state`() {
        PopupTriggerLimiter.canTrigger(intervalMillis = 60_000)
        assertFalse(PopupTriggerLimiter.canTrigger(intervalMillis = 60_000))
        PopupTriggerLimiter.reset()
        assertTrue(PopupTriggerLimiter.canTrigger(intervalMillis = 60_000))
    }
}