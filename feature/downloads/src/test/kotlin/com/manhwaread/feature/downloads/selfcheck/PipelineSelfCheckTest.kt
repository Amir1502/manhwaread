package com.manhwaread.feature.downloads.selfcheck

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.OverlayCompositor
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipelineSelfCheckTest {
    private class FakeAnalyzer : ChapterAnalyzer {
        var outcome: DomainResult<List<TextSegment>> = DomainResult.success(listOf(koreanSegment()))
        var lastPages: List<PageRef> = emptyList()
            private set

        override suspend fun analyze(job: ChapterJob, pages: List<PageRef>): DomainResult<List<TextSegment>> {
            lastPages = pages
            return outcome
        }
    }

    private class FakeCompositor : OverlayCompositor {
        var lastTranslated: List<TranslatedSegment> = emptyList()
            private set

        override suspend fun composite(
            job: ChapterJob,
            segments: List<TextSegment>,
            translated: List<TranslatedSegment>,
        ): DomainResult<List<OverlaySpec>> {
            lastTranslated = translated
            return DomainResult.success(listOf(emptySpec()))
        }
    }

    @Test
    fun `offline run reaches DONE with artifacts`() = runTest {
        val analyzer = FakeAnalyzer()
        val compositor = FakeCompositor()
        val selfCheck = PipelineSelfCheck(
            analyzerFactory = SelfCheckAnalyzerFactory { analyzer },
            compositor = compositor,
            pageFactory = SyntheticPageFactory { listOf(byteArrayOf(1, 2)) },
        )

        val result = selfCheck.run()

        assertTrue(result.success)
        assertEquals(StageStatus.DONE, result.status)
        assertEquals(1, result.segments)
        assertEquals(1, result.overlays)
        assertNull(result.error)
        // Загрузчик вернул ссылку на единственную синтетическую страницу.
        assertEquals(listOf(0), analyzer.lastPages.map { page -> page.index })
        // Эхо-перевод дошёл до типографики (офлайн, без провайдера).
        assertEquals("RU: 안녕", compositor.lastTranslated.single().text)
    }

    @Test
    fun `analyzer failure yields FAILED result with error`() = runTest {
        val analyzer = FakeAnalyzer()
        analyzer.outcome = DomainResult.failure(AppError.OcrFailed)
        val selfCheck = PipelineSelfCheck(
            analyzerFactory = SelfCheckAnalyzerFactory { analyzer },
            compositor = FakeCompositor(),
            pageFactory = SyntheticPageFactory { listOf(byteArrayOf(1)) },
        )

        val result = selfCheck.run()

        assertFalse(result.success)
        assertEquals(StageStatus.FAILED, result.status)
        assertNotNull(result.error)
        assertEquals(0, result.overlays)
    }

    @Test
    fun `empty analysis still completes pipeline`() = runTest {
        val analyzer = FakeAnalyzer()
        analyzer.outcome = DomainResult.success(emptyList())
        val selfCheck = PipelineSelfCheck(
            analyzerFactory = SelfCheckAnalyzerFactory { analyzer },
            compositor = FakeCompositor(),
            pageFactory = SyntheticPageFactory { listOf(byteArrayOf(1)) },
        )

        val result = selfCheck.run()

        assertTrue(result.success)
        assertEquals(0, result.segments)
    }

    private companion object {
        fun koreanSegment() = TextSegment(
            id = "s1",
            bubbleId = "b1",
            pageIndex = 0,
            ocrText = "안녕",
            ocrLang = DetectedLang.KO,
            ocrConfidence = 1f,
            readingOrder = 0,
        )

        fun emptySpec() = OverlaySpec(
            bubbleId = "b1",
            pageIndex = 0,
            lines = emptyList(),
            sizePx = 10f,
            lineSpacingMult = 1f,
            letterSpacing = 0f,
            scaleX = 1f,
        )
    }
}
