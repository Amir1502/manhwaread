package com.manhwaread.core.pipeline

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

// ——— Заглушки стадий: фиксированный ответ + счётчик вызовов. ———

private class StubDownloader(private val result: DomainResult<List<PageRef>>) : ChapterDownloader {
    var calls = 0
    override suspend fun download(job: ChapterJob): DomainResult<List<PageRef>> {
        calls++
        return result
    }
}

private class StubAnalyzer(private val result: DomainResult<List<TextSegment>>) : ChapterAnalyzer {
    var calls = 0
    override suspend fun analyze(job: ChapterJob, pages: List<PageRef>): DomainResult<List<TextSegment>> {
        calls++
        return result
    }
}

private class StubTranslator(private val result: DomainResult<List<TranslatedSegment>>) : SegmentTranslator {
    var calls = 0
    override suspend fun translate(segments: List<TextSegment>): DomainResult<List<TranslatedSegment>> {
        calls++
        return result
    }
}

private class StubCompositor(private val result: DomainResult<List<OverlaySpec>>) : OverlayCompositor {
    var calls = 0
    override suspend fun composite(
        job: ChapterJob,
        segments: List<TextSegment>,
        translated: List<TranslatedSegment>,
    ): DomainResult<List<OverlaySpec>> {
        calls++
        return result
    }
}

// Фикстура: in-memory хранилища + координатор со стадиями-заглушками.
private class Fixture(
    downloader: DomainResult<List<PageRef>> = DomainResult.success(listOf(PageRef(0, "http://p0"))),
    analyzer: DomainResult<List<TextSegment>> = DomainResult.success(listOf(PipelineFixtures.segment("s1"))),
    translator: DomainResult<List<TranslatedSegment>> = DomainResult.success(
        listOf(TranslatedSegment("s1", "привет")),
    ),
    compositor: DomainResult<List<OverlaySpec>> = DomainResult.success(listOf(PipelineFixtures.overlay("b-s1"))),
) {
    val store = InMemoryChapterJobStore()
    val overlays = InMemoryOverlayStore()
    val stubDownloader = StubDownloader(downloader)
    val stubAnalyzer = StubAnalyzer(analyzer)
    val stubTranslator = StubTranslator(translator)
    val stubCompositor = StubCompositor(compositor)
    val coordinator = ChapterPipelineCoordinator(
        store,
        PipelineStages(stubDownloader, stubAnalyzer, stubTranslator, stubCompositor, overlays),
    )
}

class ChapterPipelineCoordinatorTest {
    @Test
    fun `happy path completes all stages and saves overlays`() = runBlocking {
        val fx = Fixture()
        fx.store.enqueue(PipelineFixtures.testJob())
        val result = fx.coordinator.run("job-1")
        assertTrue(result is DomainResult.Success)
        assertEquals(StageStatus.DONE, fx.store.findById("job-1")?.state?.status)
        assertEquals(1, fx.stubDownloader.calls)
        assertEquals(1, fx.stubAnalyzer.calls)
        assertEquals(1, fx.stubTranslator.calls)
        assertEquals(1, fx.stubCompositor.calls)
        assertEquals(1, fx.overlays.loadChapter(100L).size)
    }

    @Test
    fun `unknown job fails with Unknown error`() = runBlocking {
        val fx = Fixture()
        val result = fx.coordinator.run("missing")
        val failure = result as DomainResult.Failure
        assertTrue(failure.error is AppError.Unknown)
        assertEquals(0, fx.stubDownloader.calls)
    }

    @Test
    fun `download failure marks job failed and stops chain`() = runBlocking {
        val fx = Fixture(downloader = DomainResult.failure(AppError.Network(IOException("net"))))
        fx.store.enqueue(PipelineFixtures.testJob())
        val result = fx.coordinator.run("job-1")
        assertTrue(result is DomainResult.Failure)
        val state = fx.store.findById("job-1")!!.state
        assertEquals(StageStatus.FAILED, state.status)
        assertEquals(1, state.attempts)
        assertTrue(state.lastError is AppError.Network)
        assertEquals(0, fx.stubAnalyzer.calls)
    }

    @Test
    fun `analyzer failure stops before translation`() = runBlocking {
        val fx = Fixture(analyzer = DomainResult.failure(AppError.OcrFailed))
        fx.store.enqueue(PipelineFixtures.testJob())
        assertTrue(fx.coordinator.run("job-1") is DomainResult.Failure)
        assertEquals(StageStatus.FAILED, fx.store.findById("job-1")!!.state.status)
        assertEquals(1, fx.stubDownloader.calls)
        assertEquals(0, fx.stubTranslator.calls)
    }

    @Test
    fun `translator failure stops before compositing`() = runBlocking {
        val fx = Fixture(translator = DomainResult.failure(AppError.ProviderAuth))
        fx.store.enqueue(PipelineFixtures.testJob())
        val result = fx.coordinator.run("job-1")
        val failure = result as DomainResult.Failure
        assertTrue(failure.error is AppError.ProviderAuth)
        assertEquals(0, fx.stubCompositor.calls)
        assertTrue(fx.overlays.loadChapter(100L).isEmpty())
    }

    @Test
    fun `compositor failure marks job failed without saving overlays`() = runBlocking {
        val fx = Fixture(compositor = DomainResult.failure(AppError.TypesetOverflow("b-s1")))
        fx.store.enqueue(PipelineFixtures.testJob())
        assertTrue(fx.coordinator.run("job-1") is DomainResult.Failure)
        val state = fx.store.findById("job-1")!!.state
        assertEquals(StageStatus.FAILED, state.status)
        assertTrue(state.lastError is AppError.TypesetOverflow)
        assertTrue(fx.overlays.loadChapter(100L).isEmpty())
    }

    @Test
    fun `done job run returns success without stage calls`() = runBlocking {
        val fx = Fixture()
        fx.store.enqueue(PipelineFixtures.testJob(state = JobState(status = StageStatus.DONE)))
        assertTrue(fx.coordinator.run("job-1") is DomainResult.Success)
        assertEquals(0, fx.stubDownloader.calls)
    }

    @Test
    fun `failed job reruns full chain to done`() = runBlocking {
        val fx = Fixture()
        fx.store.enqueue(PipelineFixtures.testJob(state = JobState(status = StageStatus.FAILED, attempts = 1)))
        assertTrue(fx.coordinator.run("job-1") is DomainResult.Success)
        assertEquals(StageStatus.DONE, fx.store.findById("job-1")!!.state.status)
        assertEquals(1, fx.stubDownloader.calls)
    }

    @Test
    fun `cancelled job reruns full chain to done`() = runBlocking {
        val fx = Fixture()
        fx.store.enqueue(PipelineFixtures.testJob(state = JobState(status = StageStatus.CANCELLED)))
        assertTrue(fx.coordinator.run("job-1") is DomainResult.Success)
        assertEquals(StageStatus.DONE, fx.store.findById("job-1")!!.state.status)
    }
}
