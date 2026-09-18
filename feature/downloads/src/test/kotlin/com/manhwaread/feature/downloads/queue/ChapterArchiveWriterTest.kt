package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import com.manhwaread.core.vision.TextSegment
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
        val bubble = Bubble(
            id = "b1",
            pageIndex = 0,
            polygon = emptyList(),
            bounds = RectF(left = 0f, top = 0f, right = 100f, bottom = 50f),
            kind = BubbleKind.SPEECH,
        )
        // Намеренно в обратном порядке: писатель сортирует по readingOrder.
        val segments = listOf(
            segment(id = "s2", order = 1, text = "둘"),
            segment(id = "s1", order = 0, text = "하나"),
        )
        val specs = listOf(
            OverlaySpec(
                bubbleId = "b1",
                pageIndex = 0,
                lines = listOf(
                    OverlayLine(text = "Раз", baselineStart = PointF(x = 1f, y = 2f), widthPx = 10f),
                    OverlayLine(text = "Два", baselineStart = PointF(x = 1f, y = 12f), widthPx = 10f),
                ),
                sizePx = 10f,
                lineSpacingMult = 1f,
                letterSpacing = 0f,
                scaleX = 1f,
            ),
        )
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
        val bubble = Bubble(
            id = "b1",
            pageIndex = 0,
            polygon = emptyList(),
            bounds = RectF(left = 0f, top = 0f, right = 100f, bottom = 50f),
            kind = BubbleKind.SPEECH,
        )
        writer.writeTranslatedChapter(CHAPTER_ID, "Глава 10", listOf(bubble), listOf(segment("s1", 0, "текст")), emptyList())
        val area = ChapterMetaJson.decode(metaFile().readText()).bubbles.single()
        assertEquals("текст", area.originalText)
        assertNull(area.translatedText)
    }

    private fun segment(id: String, order: Int, text: String) = TextSegment(
        id = id,
        bubbleId = "b1",
        pageIndex = 0,
        ocrText = text,
        ocrLang = DetectedLang.KO,
        ocrConfidence = 1f,
        readingOrder = order,
    )

    private companion object {
        const val CHAPTER_ID = 10L
    }
}
