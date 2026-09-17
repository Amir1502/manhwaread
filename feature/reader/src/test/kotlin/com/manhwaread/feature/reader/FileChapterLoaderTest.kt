package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlayAlign
import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileChapterLoaderTest {
    @TempDir
    lateinit var tempDir: File

    private val sizeReader = object : ImageSizeReader {
        override fun read(file: File): Pair<Int, Int> = 800 to 1200
    }

    private fun loader() = FileChapterLoader(imageSizeReader = sizeReader)

    private fun writePage(name: String) {
        File(tempDir, name).writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `pages sorted by numeric suffix not lexicographically`() = runBlocking {
        writePage("page2.png")
        writePage("page10.png")
        writePage("page1.png")
        File(tempDir, "notes.txt").writeText("не изображение")
        val chapter = loader().loadChapter(tempDir)
        assertEquals(
            listOf("page1", "page2", "page10"),
            chapter.pages.map { it.imageFile.nameWithoutExtension },
        )
        assertEquals(3, chapter.pages.size)
        assertEquals(800, chapter.pages[0].widthPx)
        assertEquals(1200, chapter.pages[0].heightPx)
    }

    @Test
    fun `overlays and bubbles attach to their pages`() = runBlocking {
        writePage("page1.png")
        writePage("page2.png")
        val spec = OverlaySpec(
            bubbleId = "b1",
            pageIndex = 1,
            lines = listOf(OverlayLine("текст", PointF(1f, 2f), 10f)),
            sizePx = 18f,
            lineSpacingMult = 1f,
            letterSpacing = 0f,
            scaleX = 1f,
            align = OverlayAlign.CENTER,
        )
        File(tempDir, "overlays.json").writeText(OverlaySpecJson.encode(listOf(spec)))
        val meta = ChapterMeta(
            title = "Глава 7",
            bubbles = listOf(
                BubbleHitArea("b1", 1, RectF(0f, 0f, 50f, 50f), "원문", "перевод"),
            ),
        )
        File(tempDir, "chapter.json").writeText(ChapterMetaJson.encode(meta))
        val chapter = loader().loadChapter(tempDir)
        assertEquals("Глава 7", chapter.title)
        assertTrue(chapter.pages[0].overlays.isEmpty())
        assertEquals(1, chapter.pages[1].overlays.size)
        assertEquals("b1", chapter.pages[1].overlays[0].bubbleId)
        assertEquals(1, chapter.pages[1].bubbles.size)
        assertEquals("перевод", chapter.pages[1].bubbles[0].translatedText)
    }

    @Test
    fun `missing meta files yield chapter without overlays`() = runBlocking {
        writePage("page1.png")
        val chapter = loader().loadChapter(tempDir)
        assertEquals("", chapter.title)
        assertEquals(1, chapter.pages.size)
        assertTrue(chapter.pages[0].overlays.isEmpty())
        assertTrue(chapter.pages[0].bubbles.isEmpty())
    }

    @Test
    fun `loading a plain file throws IllegalArgumentException`() {
        val file = File(tempDir, "not-a-dir.png")
        file.writeBytes(byteArrayOf(0))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { loader().loadChapter(file) }
        }
    }
}
