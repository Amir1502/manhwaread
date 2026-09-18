package com.manhwaread.source.asura

import com.manhwaread.core.common.AppError
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.SourceException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AsuraSourceTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val now = 1_700_000_000_000L

    private val listHtml = """
        <html><body>
        <a href="/comic/solo-leveling"><img alt="Solo Leveling" src="https://cdn.asura/1.jpg"></a>
        </body></html>
    """.trimIndent()

    private val detailsHtml = """
        <html><body>
        <h1>Solo Leveling</h1>
        <a href="/comic/solo-leveling/chapter-110"><span>Chapter 110</span><span>2 days ago</span></a>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><script id="__NEXT_DATA__">{"props":{"pageProps":{"chapter":{"images":["https://tooning.asura/1.png"]}}}}</script></html>
    """.trimIndent()

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun source(): AsuraSource {
        val manifest = DEFAULT_ASURA_MANIFEST.copy(baseUrl = server.url("").toString().trimEnd('/'))
        return AsuraSource(client, manifest, clock = { now })
    }

    @Test
    fun `popular requests sorted list`() = runTest {
        server.enqueue(MockResponse().setBody(listHtml))
        val page = source().getPopular(2)
        assertEquals(1, page.mangas.size)
        assertEquals("/comics?page=2&sort=popular", server.takeRequest().path)
    }

    @Test
    fun `latest requests latest sort`() = runTest {
        server.enqueue(MockResponse().setBody(listHtml))
        source().getLatest(1)
        assertEquals("/comics?page=1&sort=latest", server.takeRequest().path)
    }

    @Test
    fun `search adds name parameter`() = runTest {
        server.enqueue(MockResponse().setBody(listHtml))
        source().search("solo", emptyList(), 1)
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.contains("name=solo"), path)
        assertTrue(path.contains("page=1"), path)
    }

    @Test
    fun `details returns initialized manga`() = runTest {
        server.enqueue(MockResponse().setBody(detailsHtml))
        val details = source().getDetails(SManga(url = "/comic/solo-leveling", title = "x", sourceId = 3L))
        assertEquals("Solo Leveling", details.title)
        assertTrue(details.initialized)
        assertEquals("/comic/solo-leveling", server.takeRequest().path)
    }

    @Test
    fun `chapters come from details page`() = runTest {
        server.enqueue(MockResponse().setBody(detailsHtml))
        val chapters = source().getChapterList(SManga(url = "/comic/solo-leveling", title = "x", sourceId = 3L))
        assertEquals(1, chapters.size)
        assertEquals("/comic/solo-leveling/chapter-110", chapters[0].url)
        assertEquals(now - 2 * 86_400_000L, chapters[0].dateUpload)
    }

    @Test
    fun `pages come from next data`() = runTest {
        server.enqueue(MockResponse().setBody(chapterHtml))
        val pages = source().getPageList(SChapter(url = "/comic/solo-leveling/chapter-110", name = "Chapter 110"))
        assertEquals(1, pages.size)
        assertEquals("https://tooning.asura/1.png", pages[0].imageUrl)
    }

    @Test
    fun `broken layout throws SourceLayoutChanged with manifest hint`() {
        server.enqueue(MockResponse().setBody("<html><body><p>completely new design</p></body></html>"))
        val error = assertThrows(SourceException::class.java) {
            runBlocking { source().getPageList(SChapter(url = "/comic/x/chapter-1", name = "Chapter 1")) }
        }
        assertEquals(AppError.SourceLayoutChanged, error.error)
        assertTrue(error.message.orEmpty().contains("update the manifest"), error.message.orEmpty())
    }

    @Test
    fun `404 maps to SourceUnavailable`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val error = assertThrows(SourceException::class.java) {
            runBlocking { source().getPopular(1) }
        }
        assertEquals(AppError.SourceUnavailable, error.error)
    }

    @Test
    fun `429 maps to RateLimited`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
        val error = assertThrows(SourceException::class.java) {
            runBlocking { source().getPopular(1) }
        }
        assertEquals(AppError.RateLimited(2_000L), error.error)
    }

    @Test
    fun `server error maps to Network`() {
        server.enqueue(MockResponse().setResponseCode(502))
        val error = assertThrows(SourceException::class.java) {
            runBlocking { source().getPopular(1) }
        }
        assertTrue(error.error is AppError.Network)
    }
}
