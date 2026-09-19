package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.AppError
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.InMemoryPageStore
import com.manhwaread.source.api.InMemorySourceRegistry
import com.manhwaread.source.api.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SourceChapterDownloaderTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val pageStore = InMemoryPageStore()
    private lateinit var source: FakePageSource
    private lateinit var downloader: SourceChapterDownloader

    private val job = ChapterJob(
        id = "job-1",
        ref = ChapterRef(sourceId = 7L, mangaId = 1L, chapterId = 10L, chapterUrl = "/ch/10"),
    )

    private val pageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 7, 7)

    private val htmlBytes = "<html><body>Anti-bot check</body></html>".toByteArray(Charsets.UTF_8)

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        source = FakePageSource(baseUrl = server.url("/").toString())
        val registry = InMemorySourceRegistry()
        registry.register(source)
        downloader = SourceChapterDownloader(registry, client, pageStore, imageValidator = ::fakeImageValidator)
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun enqueuePage() {
        server.enqueue(MockResponse().setBody(Buffer().write(pageBytes)))
    }

    @Test
    fun `download saves pages and returns refs with referer`() = runBlocking {
        source.pages = listOf(Page(0, server.url("/p0.png").toString()), Page(1, server.url("/p1.png").toString()))
        enqueuePage()
        enqueuePage()
        val refs = downloader.download(job).getOrNull()
        assertEquals(listOf(0, 1), refs?.map { ref -> ref.index })
        assertEquals("/ch/10", source.lastChapterUrl)
        assertArrayEquals(pageBytes, pageStore.loadPage(10L, 0))
        assertArrayEquals(pageBytes, pageStore.loadPage(10L, 1))
        assertEquals(source.baseUrl, server.takeRequest().getHeader("Referer"))
    }

    @Test
    fun `cached page skips network`() = runBlocking {
        // Валидный растр в кэше (настоящий PNG): страница повторно не скачивается.
        val cached = onePixelPngBytes
        pageStore.savePage(10L, 0, cached)
        source.pages = listOf(Page(0, server.url("/p0.png").toString()), Page(1, server.url("/p1.png").toString()))
        enqueuePage()
        val refs = downloader.download(job).getOrNull()
        assertEquals(2, refs?.size)
        assertEquals(1, server.requestCount)
        assertArrayEquals(cached, pageStore.loadPage(10L, 0))
    }

    @Test
    fun `corrupt cached page is redownloaded`() = runBlocking {
        // Битый кэш (HTML вместо растра) считается отсутствующей страницей.
        pageStore.savePage(10L, 0, htmlBytes)
        source.pages = listOf(Page(0, server.url("/p0.png").toString()))
        server.enqueue(MockResponse().setBody(Buffer().write(onePixelPngBytes)))
        val refs = downloader.download(job).getOrNull()
        assertEquals(listOf(0), refs?.map { ref -> ref.index })
        assertEquals(1, server.requestCount)
        assertArrayEquals(onePixelPngBytes, pageStore.loadPage(10L, 0))
    }

    @Test
    fun `html body with 200 is rejected as page`() = runBlocking {
        // Антибот-страница с кодом 200: загрузка падает, мусор в кэш не пишется.
        source.pages = listOf(Page(0, server.url("/p0.png").toString()))
        server.enqueue(MockResponse().setBody(Buffer().write(htmlBytes)))
        val error = downloader.download(job).errorOrNull()
        assertTrue(error is AppError.Network, "expected Network, got $error")
        assertNull(pageStore.loadPage(10L, 0))
    }

    @Test
    fun `unknown source yields SourceUnavailable`() = runBlocking {
        val unknown = ChapterJob(
            id = "job-x",
            ref = ChapterRef(sourceId = 999L, mangaId = 1L, chapterId = 10L, chapterUrl = "/ch/10"),
        )
        val error = downloader.download(unknown).errorOrNull()
        assertEquals(AppError.SourceUnavailable, error)
    }

    @Test
    fun `source failure is unwrapped from SourceException`() = runBlocking {
        source.failure = AppError.CloudflareBlocked
        val error = downloader.download(job).errorOrNull()
        assertEquals(AppError.CloudflareBlocked, error)
    }

    @Test
    fun `empty page list yields SourceLayoutChanged`() = runBlocking {
        source.pages = emptyList()
        val error = downloader.download(job).errorOrNull()
        assertEquals(AppError.SourceLayoutChanged, error)
    }

    @Test
    fun `page without url yields SourceLayoutChanged`() = runBlocking {
        source.pages = listOf(Page(0, null))
        val error = downloader.download(job).errorOrNull()
        assertEquals(AppError.SourceLayoutChanged, error)
    }

    @Test
    fun `http error yields Network failure`() = runBlocking {
        source.pages = listOf(Page(0, server.url("/p0.png").toString()))
        server.enqueue(MockResponse().setResponseCode(SERVER_ERROR))
        val error = downloader.download(job).errorOrNull()
        assertTrue(error is AppError.Network, "expected Network, got $error")
    }

    @Test
    fun `onProgress false cancels download`() {
        source.pages = listOf(Page(0, server.url("/p0.png").toString()))
        enqueuePage()
        val registry = InMemorySourceRegistry()
        registry.register(source)
        val cancellable = SourceChapterDownloader(
            registry = registry,
            client = client,
            pageStore = pageStore,
            onProgress = { _, _ -> false },
            imageValidator = ::fakeImageValidator,
        )
        assertThrows(CancellationException::class.java) {
            runBlocking { cancellable.download(job) }
        }
        // Первая страница сохранена до отмены — повтор докачает только остаток.
        runBlocking { assertArrayEquals(pageBytes, pageStore.loadPage(10L, 0)) }
    }

    private companion object {
        const val SERVER_ERROR = 500
    }
}
