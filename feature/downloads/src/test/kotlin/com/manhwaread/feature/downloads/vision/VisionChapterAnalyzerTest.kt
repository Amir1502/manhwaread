package com.manhwaread.feature.downloads.vision

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.BubbleKind
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Проход стадии ANALYZING на фейках: детектор/OCR/хранилище подставные,
// Bitmap — настоящий (Robolectric), нативные библиотеки не задействуются.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VisionChapterAnalyzerTest {
    private val job = ChapterJob(id = "job1", ref = ChapterRef(1L, 2L, CHAPTER_ID, "/ch"))
    private val pages = listOf(PageRef(0, "url0"), PageRef(1, "url1"))

    private class FakePageStore(private val stored: MutableMap<Pair<Long, Int>, ByteArray>) : PageStore {
        override suspend fun savePage(chapterId: Long, pageIndex: Int, bytes: ByteArray) {
            stored[chapterId to pageIndex] = bytes
        }

        override suspend fun loadPage(chapterId: Long, pageIndex: Int): ByteArray? = stored[chapterId to pageIndex]
    }

    // Каждый вызов — новый Bitmap: анализатор вызывает recycle() по завершении страницы.
    private class FakeDecoder : PageBitmapDecoder {
        override fun decode(bytes: ByteArray): Bitmap =
            Bitmap.createBitmap(BITMAP_SIZE_PX, BITMAP_SIZE_PX, Bitmap.Config.ARGB_8888)
    }

    private class FakeDetector(private val bubbles: List<Bubble>, private val failure: Throwable? = null) :
        BubbleDetector {
        override suspend fun detect(bitmap: Bitmap, pageIndex: Int): List<Bubble> {
            if (failure != null) {
                throw failure
            }
            return bubbles
        }
    }

    private class FakeOcr(private val linesPerPage: Map<Int, List<OcrLine>>) : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap, pageIndex: Int): List<OcrLine> =
            linesPerPage[pageIndex].orEmpty()
    }

    private fun bubble(id: String): Bubble =
        Bubble(
            id = id,
            pageIndex = 0,
            polygon = listOf(PointF(0f, 0f), PointF(3f, 0f), PointF(3f, 3f), PointF(0f, 3f)),
            bounds = RectF(0f, 0f, 3f, 3f),
            kind = BubbleKind.SPEECH,
        )

    private fun ocrLine(text: String, inside: Boolean): OcrLine {
        val bounds = if (inside) RectF(0.5f, 0.5f, 2f, 2f) else RectF(50f, 50f, 60f, 60f)
        return OcrLine(text, bounds, 0.9f, DetectedLang.KO)
    }

    private fun fullStore() = FakePageStore(
        mutableMapOf(
            (CHAPTER_ID to 0) to ByteArray(4),
            (CHAPTER_ID to 1) to ByteArray(4),
        ),
    )

    @Test
    fun `analyze returns segments for lines inside bubbles`() = runBlocking {
        val analyzer = VisionChapterAnalyzer(
            pageStore = fullStore(),
            bitmapDecoder = FakeDecoder(),
            detector = FakeDetector(listOf(bubble("b0"))),
            ocr = FakeOcr(mapOf(0 to listOf(ocrLine("안녕", inside = true), ocrLine("фон", inside = false)))),
        )
        val result = analyzer.analyze(job, pages)
        assertTrue(result is DomainResult.Success)
        val segments = (result as DomainResult.Success).value
        // Строка вне бабла отбрасывается: вписываем только в облачка.
        assertEquals(1, segments.size)
        val segment = segments[0]
        assertEquals("b0", segment.bubbleId)
        assertEquals("job1:p0:l0", segment.id)
        assertEquals("안녕", segment.ocrText)
        assertEquals(DetectedLang.KO, segment.ocrLang)
        assertEquals(0.9f, segment.ocrConfidence, CONFIDENCE_EPS)
        assertEquals(0, segment.readingOrder)
    }

    @Test
    fun `sfx lines are flagged`() = runBlocking {
        val analyzer = VisionChapterAnalyzer(
            pageStore = fullStore(),
            bitmapDecoder = FakeDecoder(),
            detector = FakeDetector(listOf(bubble("b0"))),
            ocr = FakeOcr(mapOf(0 to listOf(ocrLine("ドン", inside = true)))),
        )
        val result = analyzer.analyze(job, pages)
        val segments = (result as DomainResult.Success).value
        assertTrue(segments.single().isSfx)
    }

    @Test
    fun `reading order continues across pages`() = runBlocking {
        val analyzer = VisionChapterAnalyzer(
            pageStore = fullStore(),
            bitmapDecoder = FakeDecoder(),
            detector = FakeDetector(listOf(bubble("b0"))),
            ocr = FakeOcr(
                mapOf(
                    0 to listOf(ocrLine("a", inside = true), ocrLine("b", inside = true)),
                    1 to listOf(ocrLine("c", inside = true)),
                ),
            ),
        )
        val result = analyzer.analyze(job, pages)
        val segments = (result as DomainResult.Success).value
        assertEquals(listOf(0, 1, 2), segments.map { it.readingOrder })
        assertEquals(listOf(0, 0, 1), segments.map { it.pageIndex })
    }

    @Test
    fun `missing page bytes fail with VisionFailed`() = runBlocking {
        val analyzer = VisionChapterAnalyzer(
            pageStore = FakePageStore(mutableMapOf((CHAPTER_ID to 0) to ByteArray(4))),
            bitmapDecoder = FakeDecoder(),
            detector = FakeDetector(listOf(bubble("b0"))),
            ocr = FakeOcr(emptyMap()),
        )
        val result = analyzer.analyze(job, pages)
        assertTrue(result is DomainResult.Failure)
        assertEquals(AppError.VisionFailed, (result as DomainResult.Failure).error)
    }

    @Test
    fun `undecodable page fails with VisionFailed`() = runBlocking {
        val analyzer = VisionChapterAnalyzer(
            pageStore = fullStore(),
            bitmapDecoder = object : PageBitmapDecoder {
                override fun decode(bytes: ByteArray): Bitmap? = null
            },
            detector = FakeDetector(listOf(bubble("b0"))),
            ocr = FakeOcr(emptyMap()),
        )
        val result = analyzer.analyze(job, pages)
        assertTrue(result is DomainResult.Failure)
        assertEquals(AppError.VisionFailed, (result as DomainResult.Failure).error)
    }

    @Test
    fun `detector VisionException maps to VisionFailed`() = runBlocking {
        val analyzer = VisionChapterAnalyzer(
            pageStore = fullStore(),
            bitmapDecoder = FakeDecoder(),
            detector = FakeDetector(emptyList(), VisionException("native down")),
            ocr = FakeOcr(emptyMap()),
        )
        val result = analyzer.analyze(job, pages)
        assertTrue(result is DomainResult.Failure)
        assertEquals(AppError.VisionFailed, (result as DomainResult.Failure).error)
    }

    @Test
    fun `detector unexpected error maps to Unknown`() = runBlocking {
        val cause = IllegalStateException("boom")
        val analyzer = VisionChapterAnalyzer(
            pageStore = fullStore(),
            bitmapDecoder = FakeDecoder(),
            detector = FakeDetector(emptyList(), cause),
            ocr = FakeOcr(emptyMap()),
        )
        val result = analyzer.analyze(job, pages)
        assertTrue(result is DomainResult.Failure)
        assertEquals(AppError.Unknown(cause), (result as DomainResult.Failure).error)
    }

    @Test
    fun `mapVisionError maps by type`() {
        val vision = mapVisionError(VisionException("x"))
        assertEquals(AppError.VisionFailed, vision)
        val cause = RuntimeException("y")
        assertEquals(AppError.Unknown(cause), mapVisionError(cause))
    }

    private companion object {
        const val CHAPTER_ID = 3L
        const val BITMAP_SIZE_PX = 4
        const val CONFIDENCE_EPS = 0.0001f
    }
}
