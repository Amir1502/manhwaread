package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlayAlign
import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlaySpecJsonTest {
    private val fullSpec = OverlaySpec(
        bubbleId = "bubble-1",
        pageIndex = 3,
        lines = listOf(
            OverlayLine(text = "Первая строка", baselineStart = PointF(x = 10.5f, y = 30.25f), widthPx = 120f),
            OverlayLine(text = "Вторая", baselineStart = PointF(x = 12f, y = 55f), widthPx = 48f),
        ),
        sizePx = 22.5f,
        lineSpacingMult = 0.95f,
        letterSpacing = -0.4f,
        scaleX = 0.85f,
        fontFamily = "sans-serif-condensed",
        colorArgb = -16777216,
        align = OverlayAlign.START,
    )

    @Test
    fun `roundtrip preserves all fields`() {
        val decoded = OverlaySpecJson.decode(OverlaySpecJson.encode(listOf(fullSpec)))
        assertEquals(listOf(fullSpec), decoded)
    }

    @Test
    fun `roundtrip preserves multiple specs and order`() {
        val second = fullSpec.copy(bubbleId = "bubble-2", pageIndex = 4, lines = emptyList(), fontFamily = null)
        val decoded = OverlaySpecJson.decode(OverlaySpecJson.encode(listOf(fullSpec, second)))
        assertEquals(2, decoded.size)
        assertEquals("bubble-1", decoded[0].bubbleId)
        assertEquals("bubble-2", decoded[1].bubbleId)
    }

    @Test
    fun `empty list encodes to empty array`() {
        assertEquals("[]", OverlaySpecJson.encode(emptyList()))
        assertTrue(OverlaySpecJson.decode("[]").isEmpty())
    }

    @Test
    fun `malformed json throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { OverlaySpecJson.decode("{not json") }
    }

    @Test
    fun `json object instead of array throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { OverlaySpecJson.decode("""{"a":1}""") }
    }

    @Test
    fun `missing field throws IllegalArgumentException naming the field`() {
        val withoutSize = """[{"bubbleId":"b","pageIndex":0,"lines":[]}]"""
        val error = assertThrows(IllegalArgumentException::class.java) { OverlaySpecJson.decode(withoutSize) }
        assertTrue(error.message!!.contains("sizePx"), "message should name missing field: ${error.message}")
    }
}
