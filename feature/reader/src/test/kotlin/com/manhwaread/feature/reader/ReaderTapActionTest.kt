package com.manhwaread.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderTapActionTest {
    private val width = 1080f
    private val height = 2400f

    private fun tap(x: Float, y: Float, mode: ReaderMode) = resolveTapAction(x, y, width, height, mode)

    @Test
    fun `vertical modes split screen into horizontal bands`() {
        listOf(ReaderMode.WEBTOON, ReaderMode.VERTICAL).forEach { mode ->
            assertEquals(ReaderTapAction.PREVIOUS, tap(x = 540f, y = 100f, mode = mode))
            assertEquals(ReaderTapAction.TOGGLE_CHROME, tap(x = 540f, y = 1200f, mode = mode))
            assertEquals(ReaderTapAction.NEXT, tap(x = 540f, y = 2300f, mode = mode))
            // Горизонталь в вертикальных режимах не важна: левый край по центру — панели.
            assertEquals(ReaderTapAction.TOGGLE_CHROME, tap(x = 10f, y = 1200f, mode = mode))
        }
    }

    @Test
    fun `left to right pages with columns`() {
        assertEquals(ReaderTapAction.PREVIOUS, tap(x = 100f, y = 1200f, mode = ReaderMode.LTR))
        assertEquals(ReaderTapAction.TOGGLE_CHROME, tap(x = 540f, y = 1200f, mode = ReaderMode.LTR))
        assertEquals(ReaderTapAction.NEXT, tap(x = 1000f, y = 1200f, mode = ReaderMode.LTR))
        // Вертикаль в горизонтальных режимах не важна.
        assertEquals(ReaderTapAction.NEXT, tap(x = 1000f, y = 50f, mode = ReaderMode.LTR))
    }

    @Test
    fun `right to left mirrors the columns`() {
        assertEquals(ReaderTapAction.NEXT, tap(x = 100f, y = 1200f, mode = ReaderMode.RTL))
        assertEquals(ReaderTapAction.TOGGLE_CHROME, tap(x = 540f, y = 1200f, mode = ReaderMode.RTL))
        assertEquals(ReaderTapAction.PREVIOUS, tap(x = 1000f, y = 1200f, mode = ReaderMode.RTL))
    }

    @Test
    fun `exact third boundaries belong to the central zone`() {
        assertEquals(ReaderTapAction.TOGGLE_CHROME, tap(x = width / 3f, y = 1200f, mode = ReaderMode.LTR))
        assertEquals(ReaderTapAction.TOGGLE_CHROME, tap(x = width * 2f / 3f, y = 1200f, mode = ReaderMode.LTR))
        assertEquals(ReaderTapAction.TOGGLE_CHROME, tap(x = 540f, y = height / 3f, mode = ReaderMode.WEBTOON))
    }

    @Test
    fun `unknown viewport always toggles chrome`() {
        assertEquals(ReaderTapAction.TOGGLE_CHROME, resolveTapAction(10f, 10f, 0f, 0f, ReaderMode.LTR))
        assertEquals(ReaderTapAction.TOGGLE_CHROME, resolveTapAction(10f, 10f, width, -1f, ReaderMode.WEBTOON))
    }
}
