package com.manhwaread.source.mangadex

import com.manhwaread.core.common.AppError
import com.manhwaread.core.network.CloudflareInterceptor
import com.manhwaread.core.network.NoopChallengeSolver
import com.manhwaread.core.network.RateLimiter
import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.SourceException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MangaDexSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: MangaDexSource

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        source = newSource()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun newSource(client: OkHttpClient = OkHttpClient()): MangaDexSource = MangaDexSource(
        client = client,
        baseApiUrl = server.url("/").toString().trimEnd('/'),
        rateLimiter = RateLimiter(maxConcurrent = 5, minIntervalMs = 0L),
    )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("mangadex/$name")) { "fixture $name not found" }
            .bufferedReader()
            .use { it.readText() }

    private fun enqueueJson(name: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture(name)),
        )
    }

    private fun mangaStub(url: String = "/manga/m1") = SManga(url = url, title = "stub", sourceId = MangaDexSource.SOURCE_ID)

    @Test
    fun `contract constants match the spec`() {
        assertEquals(1L, source.id)
        assertEquals("MangaDex", source.name)
        assertEquals("multi", source.lang)
        assertTrue(source.supportsSearch)
        assertFalse(source.isNsfw)
        assertTrue(source.baseUrl.startsWith("http"))
    }

    @Test
    fun `popular parses manga list and builds expected query`() = runBlocking {
        enqueueJson("popular.json")
        val page = source.getPopular(1)

        assertEquals(2, page.mangas.size)
        val first = page.mangas[0]
        assertEquals("/manga/a0a66f7b-1111-4c60-b1b4-9f1c6e0d0e01", first.url)
        assertEquals(MangaDexSource.SOURCE_ID, first.sourceId)
        assertEquals("Solo Leveling", first.title)
        assertEquals(MangaStatus.COMPLETED, first.status)
        assertEquals("Охотник E-ранга Сон Джин-Ву получает силу системы.", first.description)
        assertEquals(listOf("Action", "Fantasy"), first.genres)
        assertEquals("Chugong", first.author)
        assertNull(first.artist)
        assertEquals(
            "https://uploads.mangadex.org/covers/a0a66f7b-1111-4c60-b1b4-9f1c6e0d0e01/cover-solo.jpg.256.jpg",
            first.thumbnailUrl,
        )
        assertFalse(first.initialized)
        assertFalse(first.nsfw)

        val second = page.mangas[1]
        assertEquals("Berserk", second.title)
        assertEquals(MangaStatus.HIATUS, second.status)
        assertTrue(second.nsfw)
        assertTrue(page.hasNextPage)

        val recorded = server.takeRequest()
        assertEquals("/manga", recorded.path?.substringBefore('?'))
        val query = recorded.requestUrl?.query.orEmpty()
        assertTrue(query.contains("order[followedCount]=desc"), query)
        assertTrue(query.contains("includes[]=cover_art"), query)
        assertTrue(query.contains("limit=32"), query)
        assertTrue(query.contains("offset=0"), query)
        assertTrue(query.contains("contentRating[]=safe"), query)
    }

    @Test
    fun `latest uses latestUploadedChapter order and page offset`() = runBlocking {
        enqueueJson("latest.json")
        val page = source.getLatest(2)

        assertEquals(1, page.mangas.size)
        val only = page.mangas[0]
        assertEquals("One Piece", only.title)
        assertEquals(MangaStatus.ONGOING, only.status)
        assertNull(only.thumbnailUrl)
        assertTrue(page.hasNextPage)

        val query = server.takeRequest().requestUrl?.query.orEmpty()
        assertTrue(query.contains("order[latestUploadedChapter]=desc"), query)
        assertTrue(query.contains("offset=32"), query)
    }

    @Test
    fun `search sends title and parses result`() = runBlocking {
        enqueueJson("search.json")
        val page = source.search("Берсерк", emptyList(), 1)

        assertEquals(1, page.mangas.size)
        assertEquals("Berserk", page.mangas[0].title)
        assertEquals("Чёрный мечник Гатс охотится на апостолов.", page.mangas[0].description)
        assertFalse(page.hasNextPage)

        val query = server.takeRequest().requestUrl?.query.orEmpty()
        assertTrue(query.contains("title="), query)
        assertTrue(query.contains("order[relevance]=desc"), query)
    }

    @Test
    fun `empty search result yields empty page`() = runBlocking {
        enqueueJson("search_empty.json")
        val page = source.search("неттакоготайтла", emptyList(), 1)

        assertTrue(page.mangas.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `details fills author artist and marks initialized`() = runBlocking {
        enqueueJson("details.json")
        val stub = mangaStub("/manga/ee9d0a91-2222-4a17-8f5a-0a1c11c0e5c3")
        val details = source.getDetails(stub)

        assertEquals(stub.url, details.url)
        assertEquals("Omniscient Reader's Viewpoint", details.title)
        assertEquals("Sing N Shong", details.author)
        assertEquals("Sleepy-C", details.artist)
        assertEquals(MangaStatus.ONGOING, details.status)
        assertEquals(listOf("Action", "Drama"), details.genres)
        assertEquals("Ким Док Чхэн знает финал истории, потому что читал её десять лет.", details.description)
        assertFalse(details.nsfw)
        assertTrue(details.initialized)

        val query = server.takeRequest().requestUrl?.query.orEmpty()
        assertTrue(query.contains("includes[]=author"), query)
        assertTrue(query.contains("includes[]=artist"), query)
    }

    @Test
    fun `chapter list skips external chapters`() = runBlocking {
        enqueueJson("feed.json")
        val chapters = source.getChapterList(mangaStub())

        assertEquals(2, chapters.size)
        val first = chapters[0]
        assertEquals("/chapter/ch-1", first.url)
        assertEquals("Ch. 1 - Начало", first.name)
        assertEquals(1f, first.chapterNumber)
        assertEquals("Reapers Scans", first.scanlator)
        assertEquals(1673784000000L, first.dateUpload)

        assertEquals("/chapter/ch-2", chapters[1].url)
        assertEquals("Ch. 2", chapters[1].name)
        assertEquals(2f, chapters[1].chapterNumber)
        assertNull(chapters[1].scanlator)

        val query = server.takeRequest().requestUrl?.query.orEmpty()
        assertTrue(query.contains("translatedLanguage[]=ru"), query)
        assertTrue(query.contains("translatedLanguage[]=en"), query)
        assertTrue(query.contains("order[chapter]=desc"), query)
        assertTrue(query.contains("includes[]=scanlation_group"), query)
    }

    @Test
    fun `chapter list follows pagination until total reached`() = runBlocking {
        enqueueJson("feed_p1.json")
        enqueueJson("feed_p2.json")
        val chapters = source.getChapterList(mangaStub())

        assertEquals(2, chapters.size)
        assertEquals("/chapter/ch-p1", chapters[0].url)
        assertEquals(10f, chapters[0].chapterNumber)
        assertEquals("/chapter/ch-p2", chapters[1].url)
        assertEquals(11f, chapters[1].chapterNumber)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `empty feed yields empty chapter list`() = runBlocking {
        enqueueJson("feed_empty.json")
        val chapters = source.getChapterList(mangaStub())

        assertTrue(chapters.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `page list builds image urls from at-home response`() = runBlocking {
        enqueueJson("athome.json")
        val pages = source.getPageList(SChapter(url = "/chapter/ch-1", name = "Ch. 1"))

        assertEquals(3, pages.size)
        assertEquals(0, pages[0].index)
        assertEquals("https://s3.mangadex.network/data/abc123hash/1-xyz.png", pages[0].imageUrl)
        assertEquals("https://s3.mangadex.network/data/abc123hash/3-xyz.png", pages[2].imageUrl)
    }

    @Test
    fun `broken at-home response maps to ProviderBadResponse`() {
        enqueueJson("athome_broken.json")
        val ex = assertThrows(SourceException::class.java) {
            runBlocking { source.getPageList(SChapter(url = "/chapter/ch-1", name = "Ch. 1")) }
        }
        assertEquals(AppError.ProviderBadResponse("at-home: unexpected response shape"), ex.error)
    }

    @Test
    fun `401 maps to ProviderAuth`() {
        assertMapsTo(401, AppError.ProviderAuth)
    }

    @Test
    fun `403 maps to ProviderAuth`() {
        assertMapsTo(403, AppError.ProviderAuth)
    }

    @Test
    fun `429 maps to RateLimited with retryAfter from header`() {
        enqueueError(429, mapOf("Retry-After" to "30"))
        val ex = assertThrows(SourceException::class.java) { runBlocking { source.getPopular(1) } }
        assertEquals(AppError.RateLimited(30_000L), ex.error)
    }

    @Test
    fun `404 maps to SourceUnavailable`() {
        assertMapsTo(404, AppError.SourceUnavailable)
    }

    @Test
    fun `500 maps to Network`() {
        // Причина-исключение уникальна по идентичности — проверяем только тип ошибки.
        enqueueError(500)
        val ex = assertThrows(SourceException::class.java) { runBlocking { source.getPopular(1) } }
        assertTrue(ex.error is AppError.Network)
    }

    @Test
    fun `broken json body maps to ProviderBadResponse`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not-a-json{{{"),
        )
        val ex = assertThrows(SourceException::class.java) { runBlocking { source.getPopular(1) } }
        assertTrue(ex.error is AppError.ProviderBadResponse)
    }

    @Test
    fun `cloudflare challenge maps to CloudflareBlocked`() {
        val cfClient = OkHttpClient.Builder()
            .addInterceptor(CloudflareInterceptor(NoopChallengeSolver))
            .build()
        val cfSource = newSource(cfClient)
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Server", "cloudflare")
                .setBody("<html><title>Just a moment...</title></html>"),
        )
        val ex = assertThrows(SourceException::class.java) { runBlocking { cfSource.getPopular(1) } }
        assertEquals(AppError.CloudflareBlocked, ex.error)
    }

    private fun enqueueError(code: Int, headers: Map<String, String> = emptyMap()) {
        val response = MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(fixture("error.json"))
        headers.forEach { (key, value) -> response.setHeader(key, value) }
        server.enqueue(response)
    }

    private fun assertMapsTo(code: Int, expected: AppError) {
        enqueueError(code)
        val ex = assertThrows(SourceException::class.java) { runBlocking { source.getPopular(1) } }
        assertEquals(expected, ex.error)
    }
}
