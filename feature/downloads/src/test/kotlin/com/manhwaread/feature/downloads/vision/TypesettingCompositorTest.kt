package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.ApproximateTextMeasurer
import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.EllipseMask
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.PolygonMask
import com.manhwaread.core.vision.RectF
import com.manhwaread.core.vision.RectMask
import com.manhwaread.core.vision.TextSegment
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypesettingCompositorTest {
    private class FakeBubbleStore : BubbleStore {
        val chapters = mutableMapOf<Long, List<Bubble>>()

        override suspend fun saveBubbles(chapterId: Long, bubbles: List<Bubble>) {
            chapters[chapterId] = bubbles
        }

        override suspend fun loadBubbles(chapterId: Long): List<Bubble> = chapters[chapterId].orEmpty()

        override suspend fun clear(chapterId: Long) {
            chapters.remove(chapterId)
        }
    }

    private val bubbleStore = FakeBubbleStore()
    private val compositor = TypesettingCompositor(bubbleStore, ApproximateTextMeasurer())

    private val job = ChapterJob(
        id = "job-1",
        ref = ChapterRef(sourceId = 1L, mangaId = 2L, chapterId = 10L, chapterUrl = "/ch/10"),
    )

    private fun bubble(
        id: String,
        kind: BubbleKind = BubbleKind.SPEECH,
        polygon: List<PointF> = emptyList(),
        bounds: RectF = RectF(left = 10f, top = 10f, right = 300f, bottom = 150f),
    ) = Bubble(id = id, pageIndex = 0, polygon = polygon, bounds = bounds, kind = kind)

    private fun segment(id: String, bubbleId: String, order: Int, text: String = "orig-$id") = TextSegment(
        id = id,
        bubbleId = bubbleId,
        pageIndex = 0,
        ocrText = text,
        ocrLang = DetectedLang.KO,
        ocrConfidence = 1f,
        readingOrder = order,
    )

    @Test
    fun `composite produces spec with translated lines`() = runTest {
        bubbleStore.saveBubbles(10L, listOf(bubble("b1")))
        val specs = compositor.composite(
            job,
            segments = listOf(segment("s1", "b1", order = 0)),
            translated = listOf(TranslatedSegment("s1", "Привет мир")),
        ).getOrNull()
        assertEquals(1, specs?.size)
        val spec = requireNotNull(specs).single()
        assertEquals("b1", spec.bubbleId)
        assertEquals(0, spec.pageIndex)
        assertTrue(spec.lines.isNotEmpty())
        assertTrue(spec.lines.any { line -> line.text.contains("Привет") })
        assertTrue(spec.sizePx > 0f)
    }

    @Test
    fun `segments joined in reading order`() = runTest {
        bubbleStore.saveBubbles(10L, listOf(bubble("b1")))
        val specs = compositor.composite(
            job,
            segments = listOf(segment("s2", "b1", order = 1), segment("s1", "b1", order = 0)),
            translated = listOf(TranslatedSegment("s1", "Первый"), TranslatedSegment("s2", "Второй")),
        ).getOrNull()
        val text = requireNotNull(specs).single().lines.joinToString(" ")
        assertTrue(text.indexOf("Первый") < text.indexOf("Второй"), "order violated: $text")
    }

    @Test
    fun `bubbles without segments or translation are skipped`() = runTest {
        bubbleStore.saveBubbles(10L, listOf(bubble("b1"), bubble("b2")))
        val specs = compositor.composite(
            job,
            segments = listOf(segment("s1", "b1", order = 0)),
            translated = emptyList(),
        ).getOrNull()
        assertTrue(requireNotNull(specs).isEmpty())
    }

    @Test
    fun `blank translations are filtered out`() = runTest {
        bubbleStore.saveBubbles(10L, listOf(bubble("b1")))
        val specs = compositor.composite(
            job,
            segments = listOf(segment("s1", "b1", order = 0)),
            translated = listOf(TranslatedSegment("s1", "   ")),
        ).getOrNull()
        assertTrue(requireNotNull(specs).isEmpty())
    }

    @Test
    fun `empty bubble store yields empty specs`() = runTest {
        val specs = compositor.composite(
            job,
            segments = listOf(segment("s1", "b1", order = 0)),
            translated = listOf(TranslatedSegment("s1", "текст")),
        ).getOrNull()
        assertTrue(requireNotNull(specs).isEmpty())
    }

    @Test
    fun `lines stay inside bubble bounds`() = runTest {
        val bounds = RectF(left = 20f, top = 20f, right = 200f, bottom = 120f)
        bubbleStore.saveBubbles(10L, listOf(bubble("b1", bounds = bounds)))
        val specs = compositor.composite(
            job,
            segments = listOf(segment("s1", "b1", order = 0)),
            translated = listOf(TranslatedSegment("s1", "Длинный русский текст для проверки вписывания")),
        ).getOrNull()
        val spec = requireNotNull(specs).single()
        spec.lines.forEach { line ->
            assertTrue(line.baselineStart.y >= bounds.top, "line above bubble: ${line.baselineStart}")
            assertTrue(line.baselineStart.y <= bounds.bottom + spec.sizePx, "line below bubble")
        }
    }

    @Test
    fun `mask chosen by polygon and kind`() {
        val polygon = listOf(PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f))
        assertTrue(compositor.maskFor(bubble("p", polygon = polygon)) is PolygonMask)
        assertTrue(compositor.maskFor(bubble("n", kind = BubbleKind.NARRATION_BOX)) is RectMask)
        assertTrue(compositor.maskFor(bubble("s", kind = BubbleKind.SPEECH)) is EllipseMask)
        assertTrue(compositor.maskFor(bubble("t", kind = BubbleKind.THOUGHT)) is EllipseMask)
    }

    @Test
    fun `echo of original after normalization is not a translation`() {
        assertFalse(isUsableTranslation("  Hola   mundo ", "hola MUNDO"))
        assertFalse(isUsableTranslation("TODAVÍA", "TODAVÍA"))
    }

    @Test
    fun `translation without cyrillic is rejected`() {
        assertFalse(isUsableTranslation("TODAVÍA", "TODAVIYA"))
        assertFalse(isUsableTranslation("TODAVÍA", "todavía"))
        assertFalse(isUsableTranslation("안녕", "   "))
    }

    @Test
    fun `normal russian translation passes`() {
        assertTrue(isUsableTranslation("안녕", "Привет"))
        assertTrue(isUsableTranslation("Hola mundo", "Привет, мир!"))
    }

    @Test
    fun `bubble of junk translations yields no spec`() = runTest {
        bubbleStore.saveBubbles(10L, listOf(bubble("b1"), bubble("b2")))
        val specs = compositor.composite(
            job,
            segments = listOf(
                segment("s1", "b1", order = 0, text = "TODAVÍA"),
                segment("s2", "b2", order = 0, text = "안녕"),
            ),
            // b1 — транслитерация мусора без кириллицы, b2 — эхо оригинала.
            translated = listOf(TranslatedSegment("s1", "TODAVIYA"), TranslatedSegment("s2", "안녕")),
        ).getOrNull()
        assertTrue(requireNotNull(specs).isEmpty())
    }

    @Test
    fun `unusable segments are excluded from bubble join`() = runTest {
        bubbleStore.saveBubbles(10L, listOf(bubble("b1")))
        val specs = compositor.composite(
            job,
            segments = listOf(
                segment("s1", "b1", order = 0, text = "안녕"),
                segment("s2", "b1", order = 1, text = "TODAVÍA"),
            ),
            translated = listOf(TranslatedSegment("s1", "Привет"), TranslatedSegment("s2", "TODAVIYA")),
        ).getOrNull()
        val lines = requireNotNull(specs).single().lines.joinToString(" ")
        assertTrue(lines.contains("Привет"), "usable translation lost: $lines")
        assertFalse(lines.contains("TODAVIYA"), "junk leaked into spec: $lines")
    }
}
