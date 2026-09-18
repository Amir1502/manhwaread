package com.manhwaread.source.madara

import com.manhwaread.core.common.AppError
import com.manhwaread.source.api.Filter
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.SourceException
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

class MadaraSourceTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val now = 1_700_000_000_000L

    private val listHtml = """
        <html><body>
        <div class="page-item-detail">
          <div class="post-title"><h3><a href="/manga/solo-leveling/">Solo Leveling</a></h3></div>
        </div>
        </body></html>
    """.trimIndent()

    private val detailsHtml = """
        <html><body>
        <div class="post-title"><h1>Solo Leveling</h1></div>
        </body></html>
    """.trimIndent()

    private val holderHtml = """
        <html><body>
        <div class="post-title"><h1>Solo Leveling</h1></div>
        <div id="manga-chapters-holder" data-id="999"></div>
        </body></html>
    """.trimIndent()

    private val chaptersHtml = """
        <html><body>
        <li class="wp-manga-chapter"><a href="/manga/solo/chapter-3/">Chapter 3</a></li>
        </body></html>
    """.trimIndent()

    private val pagesHtml = """
        <html><body>
        <div class="reading-content"><img data-src="https://cdn.example/p1.png"></div>
        </body></html>
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

    private fun config(useAjaxChapters: Boolean = false) = MadaraConfig(
        id = 2L,
        name = "TestMadara",
        baseUrl = server.url("").toString().trimEnd('/'),
        adultCookie = "age_verified=1",
        useAjaxChapters = useAjaxChapters,
    )

    private fun source(useAjaxChapters: Boolean = false) =
        MadaraSource(config(useAjaxChapters), client, clock = { now })

    private fun manga(url: String = "/manga/solo-leveling") = SManga(url = url, title = "Solo", sourceId = 2L)

    @Test
    fun `popular requests ordered list and sends adult cookie`() = runTest {
        server.enqueue(MockResponse().setBody(listHtml))
        val page = source().getPopular(2)
        assertEquals(1, page.mangas.size)
        val recorded = server.takeRequest()
        assertEquals("/manga/page/2/?m_orderby=views", recorded.path)
        assertEquals("age_verified=1", recorded.getHeader("Cookie"))
    }

    @Test
    fun `latest uses latest ordering`() = runTest {
        server.enqueue(MockResponse().setBody(listHtml))
        source().getLatest(1)
        assertEquals("/manga/page/1/?m_orderby=latest", server.takeRequest().path)
    }

    @Test
    fun `search sends query as wp-manga post type`() = runTest {
        server.enqueue(MockResponse().setBody(listHtml))
        source().search("solo", emptyList(), 1)
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.startsWith("/page/1/"), path)
        assertTrue(path.contains("s=solo"), path)
        assertTrue(path.contains("post_type=wp-manga"), path)
    }

    @Test
    fun `blank search with sort filter falls back to catalog`() = runTest {
        server.enqueue(MockResponse().setBody(listHtml))
        val sort = Filter.Sort(name = "Order", options = listOf("Popular", "Latest"), selectedIndex = 1)
        source().search("  ", listOf(sort), 1)
        assertEquals("/manga/page/1/?m_orderby=latest", server.takeRequest().path)
    }

    @Test
    fun `details parses and marks initialized`() = runTest {
        server.enqueue(MockResponse().setBody(detailsHtml))
        val details = source().getDetails(manga())
        assertEquals("Solo Leveling", details.title)
        assertTrue(details.initialized)
        assertEquals("/manga/solo-leveling", server.takeRequest().path)
    }

    @Test
    fun `broken details layout throws SourceLayoutChanged`() {
        server.enqueue(MockResponse().setBody("<html><body></body></html>"))
        val error = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { source().getDetails(manga()) }
        }
        assertEquals(AppError.SourceLayoutChanged, error.error)
    }

    @Test
    fun `chapters parse from details page`() = runTest {
        server.enqueue(MockResponse().setBody(chaptersHtml))
        val chapters = source().getChapterList(manga("/manga/solo"))
        assertEquals(1, chapters.size)
        assertEquals("/manga/solo/chapter-3", chapters[0].url)
        assertEquals(3f, chapters[0].chapterNumber)
    }

    @Test
    fun `ajax fallback posts manga_get_chapters`() = runTest {
        server.enqueue(MockResponse().setBody(holderHtml))
        server.enqueue(MockResponse().setBody(chaptersHtml))
        val chapters = source(useAjaxChapters = true).getChapterList(manga("/manga/solo"))
        assertEquals(1, chapters.size)
        server.takeRequest()
        val ajax = server.takeRequest()
        assertEquals("POST", ajax.method)
        assertEquals("/wp-admin/admin-ajax.php", ajax.path)
        val body = ajax.body.readUtf8()
        assertTrue(body.contains("action=manga_get_chapters"), body)
        assertTrue(body.contains("manga=999"), body)
    }

    @Test
    fun `empty chapter list throws SourceLayoutChanged`() {
        server.enqueue(MockResponse().setBody(detailsHtml))
        val error = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { source().getChapterList(manga()) }
        }
        assertEquals(AppError.SourceLayoutChanged, error.error)
    }

    @Test
    fun `pages parse from chapter html`() = runTest {
        server.enqueue(MockResponse().setBody(pagesHtml))
        val pages = source().getPageList(SChapter(url = "/manga/solo/chapter-3", name = "Chapter 3"))
        assertEquals(1, pages.size)
        assertEquals("https://cdn.example/p1.png", pages[0].imageUrl)
    }

    @Test
    fun `404 maps to SourceUnavailable`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val error = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { source().getPopular(1) }
        }
        assertEquals(AppError.SourceUnavailable, error.error)
    }

    @Test
    fun `429 maps to RateLimited with retry-after`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3"))
        val error = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { source().getPopular(1) }
        }
        assertEquals(AppError.RateLimited(3_000L), error.error)
    }

    @Test
    fun `server error maps to Network`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val error = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { source().getPopular(1) }
        }
        assertTrue(error.error is AppError.Network)
    }
}
