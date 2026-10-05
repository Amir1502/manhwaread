package com.manhwaread.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderStatusInfoTest {
    @Test
    fun `battery percent scales level and rejects invalid extras`() {
        assertEquals(74, batteryPercent(level = 74, scale = 100))
        assertEquals(74, batteryPercent(level = 37, scale = 50))
        assertEquals(100, batteryPercent(level = 150, scale = 100))
        assertNull(batteryPercent(level = -1, scale = 100))
        assertNull(batteryPercent(level = 50, scale = 0))
    }

    @Test
    fun `clock ticks exactly on minute boundary`() {
        assertEquals(60_000L, millisUntilNextMinute(0L))
        assertEquals(1L, millisUntilNextMinute(59_999L))
        assertEquals(59_000L, millisUntilNextMinute(61_000L))
        assertEquals(1L, millisUntilNextMinute(-1L))
    }

    @Test
    fun `info strip shows page, battery and time`() {
        assertEquals("12 / 86 · 74% · 20:41", formatInfoStrip(currentPage = 11, pageCount = 86, batteryPercent = 74, time = "20:41"))
    }

    @Test
    fun `info strip omits unknown parts and clamps page`() {
        assertEquals("12 / 86", formatInfoStrip(currentPage = 11, pageCount = 86, batteryPercent = null, time = " "))
        assertEquals("86 / 86 · 5%", formatInfoStrip(currentPage = 200, pageCount = 86, batteryPercent = 5, time = null))
        assertEquals("1 / 1", formatInfoStrip(currentPage = 0, pageCount = 0, batteryPercent = null, time = null))
    }

    @Test
    fun `slider exists only for two or more pages with one step per page`() {
        assertFalse(isPageSliderVisible(0))
        assertFalse(isPageSliderVisible(1))
        assertTrue(isPageSliderVisible(2))
        assertEquals(0, pageSliderSteps(1))
        assertEquals(0, pageSliderSteps(2))
        assertEquals(84, pageSliderSteps(86))
    }

    @Test
    fun `slider value rounds to nearest page within range`() {
        assertEquals(5, sliderValueToPage(4.9999f, pageCount = 86))
        assertEquals(4, sliderValueToPage(4.4f, pageCount = 86))
        assertEquals(0, sliderValueToPage(-3f, pageCount = 86))
        assertEquals(85, sliderValueToPage(100f, pageCount = 86))
        assertEquals(0, sliderValueToPage(3f, pageCount = 0))
    }
}
