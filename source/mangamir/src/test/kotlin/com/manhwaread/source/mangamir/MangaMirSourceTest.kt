package com.manhwaread.source.mangamir

import com.manhwaread.core.common.AppError
import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.SourceException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MangaMirSourceTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    private val catalogHtml = """
        <html><body>
        <section>
          <span class="badge badge-neutral">Манхва</span>
          <span class="badge badge-info">Выпускается</span>
          <a data-card-link-type="poster" href="/manga/solo-leveling" title="Поднятие уровня в одиночку">
            <img src="https://img.mangamir.com/posters/1/solo_sm.jpeg" alt="Поднятие уровня в одиночку обложка манги">
          </a>
        </section>
        <a href="https://mangamir.com/manga?page=2">Вперёд ></a>
        </body></html>
    """.trimIndent()

    private val detailsHtml = """
        <html>
        <head>
          <script type="application/ld+json">
          {
            "@context": "https://schema.org",
            "@graph": [
              {
                "@type": "ComicSeries",
                "name": "Поднятие уровня в одиночку",
                "image": "https://img.mangamir.com/posters/1/solo.jpeg",
                "genre": ["Экшен", "Фэнтези"],
                "keywords": ["16+"],
                "hasPart": [
                  {
                    "@type": "Chapter",
                    "name": "Том 1 Глава 110",
                    "position": 110,
                    "url": "https://mangamir.com/manga/solo-leveling/tom-1-glava-110"
                  }
                ]
              }
            ]
          }
          </script>
        </head>
        <body>
          <a href="/manga?status[0]=Ongoing"><span class="badge">Выпускается</span></a>
          <div x-data="showMore">
            <div x-ref="content">
              <h2>О тайтле</h2>
              <div>Десять лет назад открылись врата.<br/>Сон Джин-у становится сильнейшим.</div>
            </div>
          </div>
          <li class="list-row">
            <a href="/manga/solo-leveling/tom-1-glava-110" title="Том 1 Глава 110">Том 1 Глава 110</a>
            <time datetime="2026-10-04T12:00:00+00:00">4 часа назад</time>
          </li>
        </body>
        </html>
    """.trimIndent()

    private val readerHtml = """
        <html><body>
        <div x-data="reader">
          <img data-number="1" src="https://img.mangamir.com/pages/1/p1.webp" width="1000" height="1500">
          <img data-number="2" src="https://img.mangamir.com/pages/1/p2.webp" width="1000" height="1500">
        </div>
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

    private fun source(): MangaMirSource {
        val base = server.url("").toString().trimEnd('/')
        return MangaMirSource(client, baseUrl = base)
    }

    @Test
    fun `getPopular sends correct parameters and headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(catalogHtml))

        val src = source()
        val result = src.getPopular(page = 1)

        assertEquals(1, result.mangas.size)
        assertTrue(result.hasNextPage)

        val recorded = server.takeRequest()
        assertEquals("/manga?sort=views&dir=desc&page=1", recorded.path)
        assertNotNullHeader(recorded.getHeader("User-Agent"))
        assertEquals("${src.baseUrl}/", recorded.getHeader("Referer"))
        assertTrue(recorded.getHeader("Accept-Language")!!.contains("ru"))
    }

    @Test
    fun `getLatest sends sort by last_chapter_id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(catalogHtml))

        val src = source()
        val result = src.getLatest(page = 2)

        assertEquals(1, result.mangas.size)

        val recorded = server.takeRequest()
        assertEquals("/manga?sort=last_chapter_id&dir=desc&page=2", recorded.path)
    }

    @Test
    fun `search encodes Cyrillic query parameter correctly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(catalogHtml))

        val src = source()
        val result = src.search("король меча", emptyList(), page = 1)

        assertEquals(1, result.mangas.size)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.startsWith("/manga?q=%D0%BA%D0%BE%D1%80%D0%BE%D0%BB%D1%8C%20%D0%BC%D0%B5%D1%87%D0%B0&page=1"))
    }

    @Test
    fun `search with blank query falls back to popular catalog`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(catalogHtml))

        val src = source()
        src.search("   ", emptyList(), page = 1)

        val recorded = server.takeRequest()
        assertEquals("/manga?sort=views&dir=desc&page=1", recorded.path)
    }

    @Test
    fun `getDetails and getChapterList use in-memory cache to avoid duplicate request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(detailsHtml))

        val src = source()
        val manga = SManga(url = "/manga/solo-leveling", title = "Solo", sourceId = src.id)

        val details = src.getDetails(manga)
        val chapters = src.getChapterList(manga)

        assertEquals("Поднятие уровня в одиночку", details.title)
        assertEquals(MangaStatus.ONGOING, details.status)
        assertEquals(1, chapters.size)
        assertEquals("Том 1 Глава 110", chapters[0].name)

        assertEquals(1, server.requestCount)
        val recorded = server.takeRequest()
        assertEquals("/manga/solo-leveling?toc", recorded.path)
    }

    @Test
    fun `getPageList requests chapter reader url and returns parsed pages`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(readerHtml))

        val src = source()
        val chapter = SChapter(url = "/manga/solo-leveling/tom-1-glava-110", name = "Ch 110")
        val pages = src.getPageList(chapter)

        assertEquals(2, pages.size)
        assertEquals("https://img.mangamir.com/pages/1/p1.webp", pages[0].imageUrl)
        assertEquals("https://img.mangamir.com/pages/1/p2.webp", pages[1].imageUrl)

        val recorded = server.takeRequest()
        assertEquals("/manga/solo-leveling/tom-1-glava-110", recorded.path)
    }

    @Test
    fun `http 401 or 403 maps to ProviderAuth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val src = source()
        val ex = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { src.getPopular(1) }
        }
        assertInstanceOf(AppError.ProviderAuth::class.java, ex.error)
    }

    @Test
    fun `http 404 maps to SourceUnavailable error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val src = source()
        val ex = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { src.getPopular(1) }
        }
        assertInstanceOf(AppError.SourceUnavailable::class.java, ex.error)
    }

    @Test
    fun `http 429 maps to RateLimited error with retryAfterMs`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "15"))

        val src = source()
        val ex = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { src.getPopular(1) }
        }
        val rateLimited = assertInstanceOf(AppError.RateLimited::class.java, ex.error)
        assertEquals(15_000L, rateLimited.retryAfterMs)
    }

    @Test
    fun `http 500 maps to Network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val src = source()
        val ex = assertThrows(SourceException::class.java) {
            kotlinx.coroutines.runBlocking { src.getPopular(1) }
        }
        assertInstanceOf(AppError.Network::class.java, ex.error)
    }

    private fun assertNotNullHeader(value: String?) {
        assertTrue(!value.isNullOrBlank())
    }
}
