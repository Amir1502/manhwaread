package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import com.manhwaread.core.vision.TextSegment
import com.manhwaread.feature.reader.BubbleMaskShape
import com.manhwaread.feature.reader.ChapterMetaJson
import com.manhwaread.feature.reader.OverlaySpecJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ChapterArchiveWriterTest {
    @TempDir
    lateinit var tempDir: File

    // lazy: инициализация полей выполняется до инъекции @TempDir (JUnit 5).
    private val dirs by lazy { ChapterDirs(tempDir) }
    private val writer by lazy { ChapterArchiveWriter(dirs, Dispatchers.Unconfined) }

    private fun metaFile() = File(dirs.dirFor(CHAPTER_ID), ChapterArchiveWriter.META_FILE_NAME)

    private fun overlaysFile() = File(dirs.dirFor(CHAPTER_ID), FileOverlayStore.OVERLAYS_FILE_NAME)

    @Test
    fun `downloaded chapter writes title-only meta and empty overlays`() = runTest {
        writer.writeDownloadedChapter(CHAPTER_ID, "Глава 10")
        val meta = ChapterMetaJson.decode(metaFile().readText())
        assertEquals("Глава 10", meta.title)
        assertTrue(meta.bubbles.isEmpty())
        assertTrue(OverlaySpecJson.decode(overlaysFile().readText()).isEmpty())
    }

    @Test
    fun `translated chapter writes hit areas and vector layer`() = runTest {
        val bubble = bubble("b1")
        // Намеренно в обратном порядке: писатель сортирует по readingOrder.
        val segments = listOf(
            segment(id = "s2", order = 1, text = "둘"),
            segment(id = "s1", order = 0, text = "하나"),
        )
        val specs = listOf(spec("b1", "Раз", "Два"))
        writer.writeTranslatedChapter(CHAPTER_ID, "Глава 10", listOf(bubble), segments, specs)

        val area = ChapterMetaJson.decode(metaFile().readText()).bubbles.single()
        assertEquals("b1", area.bubbleId)
        assertEquals(0, area.pageIndex)
        assertEquals("하나\n둘", area.originalText)
        assertEquals("Раз\nДва", area.translatedText)

        val decoded = OverlaySpecJson.decode(overlaysFile().readText())
        assertEquals(1, decoded.size)
        assertEquals("b1", decoded.single().bubbleId)
        assertEquals(listOf("Раз", "Два"), decoded.single().lines.map { line -> line.text })
    }

    @Test
    fun `bubble without spec keeps null translation`() = runTest {
        val bubble = bubble("b1")
        writer.writeTranslatedChapter(CHAPTER_ID, "Глава 10", listOf(bubble), listOf(segment("s1", 0, "текст")), emptyList())
        val area = ChapterMetaJson.decode(metaFile().readText()).bubbles.single()
        assertEquals("текст", area.originalText)
        assertNull(area.translatedText)
    }

    @Test
    fun `segment translations are preferred over spec lines`() = runTest {
        val bubble = bubble("b1")
        // Намеренно в обратном порядке: цельный перевод сортируется по readingOrder.
        val segments = listOf(
            segment(id = "s2", order = 1, text = "둘", translated = "Второй"),
            segment(id = "s1", order = 0, text = "하나", translated = "Первый"),
        )
        val specs = listOf(spec("b1", "Рваный", "перенос"))
        writer.writeTranslatedChapter(CHAPTER_ID, "Глава 10", listOf(bubble), segments, specs)

        val area = ChapterMetaJson.decode(metaFile().readText()).bubbles.single()
        assertEquals("Первый\nВторой", area.translatedText)
    }

    @Test
    fun `blank segment translations fall back to spec lines`() = runTest {
        val bubble = bubble("b1")
        val segments = listOf(segment(id = "s1", order = 0, text = "하나", translated = "   "))
        val specs = listOf(spec("b1", "Раз", "Два"))
        writer.writeTranslatedChapter(CHAPTER_ID, "Глава 10", listOf(bubble), segments, specs)

        val area = ChapterMetaJson.decode(metaFile().readText()).bubbles.single()
        assertEquals("Раз\nДва", area.translatedText)
    }

    @Test
    fun `hit area carries shape polygon and fill color`() = runTest {
        val polygon = listOf(PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f))
        val polygonBubble = bubble("bp", polygon = polygon, fillColor = FILL_COLOR)
        val narrationBubble = bubble("bn", kind = BubbleKind.NARRATION_BOX)
        val speechBubble = bubble("bs")
        writer.writeTranslatedChapter(
            CHAPTER_ID,
            "Глава 10",
            listOf(polygonBubble, narrationBubble, speechBubble),
            emptyList(),
            emptyList(),
        )

        val byId = ChapterMetaJson.decode(metaFile().readText()).bubbles.associateBy { area -> area.bubbleId }
        assertEquals(BubbleMaskShape.POLYGON, byId["bp"]?.shape)
        assertEquals(polygon, byId["bp"]?.polygon)
        assertEquals(FILL_COLOR, byId["bp"]?.fillColorArgb)
        assertEquals(BubbleMaskShape.RECT, byId["bn"]?.shape)
        assertTrue(byId["bn"]?.polygon.isNullOrEmpty())
        assertNull(byId["bn"]?.fillColorArgb)
        assertEquals(BubbleMaskShape.ELLIPSE, byId["bs"]?.shape)
    }

    private fun bubble(
        id: String,
        kind: BubbleKind = BubbleKind.SPEECH,
        polygon: List<PointF> = emptyList(),
        fillColor: Int? = null,
    ) = Bubble(
        id = id,
        pageIndex = 0,
        polygon = polygon,
        bounds = RectF(left = 0f, top = 0f, right = 100f, bottom = 50f),
        kind = kind,
        fillColor = fillColor,
    )

    private fun segment(id: String, order: Int, text: String, translated: String? = null) = TextSegment(
        id = id,
        bubbleId = "b1",
        pageIndex = 0,
        ocrText = text,
        ocrLang = DetectedLang.KO,
        ocrConfidence = 1f,
        readingOrder = order,
        translatedText = translated,
    )

    private fun spec(bubbleId: String, vararg lines: String) = OverlaySpec(
        bubbleId = bubbleId,
        pageIndex = 0,
        lines = lines.map { text -> OverlayLine(text = text, baselineStart = PointF(x = 1f, y = 2f), widthPx = 10f) },
        sizePx = 10f,
        lineSpacingMult = 1f,
        letterSpacing = 0f,
        scaleX = 1f,
    )

    private companion object {
        const val CHAPTER_ID = 10L
        const val FILL_COLOR = -65536
    }
}
