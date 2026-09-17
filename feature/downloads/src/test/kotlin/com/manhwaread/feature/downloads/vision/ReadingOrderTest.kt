package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingOrderTest {
    private val pageWidth = 1000

    private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float): OcrLine =
        OcrLine(text, RectF(left, top, right, bottom), 1f, DetectedLang.KO)

    private fun bubble(id: String, bounds: RectF): Bubble =
        Bubble(
            id = id,
            pageIndex = 0,
            polygon = listOf(PointF(bounds.left, bounds.top), PointF(bounds.right, bounds.bottom)),
            bounds = bounds,
            kind = BubbleKind.SPEECH,
        )

    @Test
    fun `webtoon order is top to bottom`() {
        val lines = listOf(
            line("third", 10f, 300f, 90f, 330f),
            line("first", 10f, 100f, 90f, 130f),
            line("second", 10f, 200f, 90f, 230f),
        )
        val ordered = assignReadingOrder(lines, pageWidth, ReadingDirection.VERTICAL_WEBTOON)
        assertEquals(listOf("first", "second", "third"), ordered.map { it.text })
        assertEquals(listOf(0, 1, 2), ordered.map { it.readingOrder })
    }

    @Test
    fun `webtoon row ties resolve left to right`() {
        val lines = listOf(
            line("right", 500f, 100f, 600f, 130f),
            line("left", 10f, 100f, 90f, 130f),
        )
        val ordered = assignReadingOrder(lines, pageWidth, ReadingDirection.VERTICAL_WEBTOON)
        assertEquals(listOf("left", "right"), ordered.map { it.text })
    }

    @Test
    fun `rtl pages read right band first`() {
        val lines = listOf(
            line("leftBand", 50f, 100f, 150f, 130f),
            line("rightBand", 850f, 100f, 950f, 130f),
        )
        val ordered = assignReadingOrder(lines, pageWidth, ReadingDirection.RTL_PAGES)
        assertEquals("rightBand", ordered[0].text)
        assertEquals(0, ordered[0].readingOrder)
        assertEquals(1, ordered[1].readingOrder)
    }

    @Test
    fun `ltr pages read left band first`() {
        val lines = listOf(
            line("rightBand", 850f, 100f, 950f, 130f),
            line("leftBand", 50f, 100f, 150f, 130f),
        )
        val ordered = assignReadingOrder(lines, pageWidth, ReadingDirection.LTR_PAGES)
        assertEquals("leftBand", ordered[0].text)
    }

    @Test
    fun `empty list returns empty`() {
        val ordered = assignReadingOrder(emptyList(), pageWidth, ReadingDirection.RTL_PAGES)
        assertTrue(ordered.isEmpty())
    }

    @Test
    fun `katakana short word is sfx`() {
        assertTrue(isSfx("ドン"))
    }

    @Test
    fun `uppercase short latin word is sfx`() {
        assertTrue(isSfx("BOOM!"))
    }

    @Test
    fun `sentence with spaces is not sfx`() {
        assertFalse(isSfx("hello world"))
    }

    @Test
    fun `hiragana word is not sfx`() {
        assertFalse(isSfx("こんにちは"))
    }

    @Test
    fun `long word is not sfx`() {
        assertFalse(isSfx("оченьдлинноеслово"))
    }

    @Test
    fun `empty text is not sfx`() {
        assertFalse(isSfx("   "))
    }

    @Test
    fun `line center inside bubble is assigned`() {
        val lines = listOf(line("inside", 100f, 100f, 200f, 140f))
        val bubbles = listOf(bubble("b0", RectF(50f, 50f, 400f, 400f)))
        val assigned = assignLinesToBubbles(lines, bubbles)
        assertEquals("b0", assigned.single().second?.id)
    }

    @Test
    fun `line outside every bubble maps to null`() {
        val lines = listOf(line("outside", 700f, 700f, 800f, 740f))
        val bubbles = listOf(bubble("b0", RectF(50f, 50f, 400f, 400f)))
        val assigned = assignLinesToBubbles(lines, bubbles)
        assertNull(assigned.single().second)
    }
}
