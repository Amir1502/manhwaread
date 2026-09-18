package com.manhwaread.source.asura

import com.manhwaread.source.api.MangaStatus
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AsuraHtmlTest {
    private val baseUrl = "https://asura.test"
    private val manifest = DEFAULT_ASURA_MANIFEST.copy(baseUrl = baseUrl)
    private val now = 1_700_000_000_000L

    private val listHtml = """
        <html><body>
        <div class="grid">
          <a href="/comic/solo-leveling">
            <img alt="Solo Leveling" src="https://cdn.asura/1.jpg">
            <h3>Solo Leveling</h3>
          </a>
          <a href="$baseUrl/comic/omniscient/">
            <img alt="Omniscient Reader" src="https://cdn.asura/2.jpg">
          </a>
          <a href="/comic/solo-leveling/chapter-110"><span>Chapter 110</span></a>
          <a href="/comic/solo-leveling"><img alt="Solo Leveling" src="https://cdn.asura/1.jpg"></a>
        </div>
        </body></html>
    """.trimIndent()

    private val detailsHtml = """
        <html><head>
        <meta property="og:image" content="https://cdn.asura/cover.jpg">
        <meta name="description" content="They say what doesn't kill you makes you stronger.">
        </head><body>
        <h1>Solo Leveling</h1>
        <span>Ongoing</span>
        <a href="/comics?genres=action">Action</a>
        <a href="/comics?genres=fantasy">Fantasy</a>
        <a href="/comic/solo-leveling/chapter-110">
          <span>Chapter 110</span><span>2 days ago</span>
        </a>
        <a href="$baseUrl/comic/solo-leveling/chapter-109">
          <span></span><span>March 3, 2024</span>
        </a>
        </body></html>
    """.trimIndent()

    private val chapterHtml = """
        <html><body>
        <script id="__NEXT_DATA__" type="application/json">
        {"props":{"pageProps":{"chapter":{"strip":{"images":[
          "https://tooning.asura/1.png","https://tooning.asura/2.webp"]}}}}}
        </script>
        </body></html>
    """.trimIndent()

    @Test
    fun `list dedupes cards and skips chapter links`() {
        val page = AsuraHtml.parseMangaList(Jsoup.parse(listHtml, baseUrl), manifest, sourceId = 3L)
        assertEquals(2, page.mangas.size)
        assertTrue(page.hasNextPage)
        assertEquals("/comic/solo-leveling", page.mangas[0].url)
        assertEquals("Solo Leveling", page.mangas[0].title)
        assertEquals("https://cdn.asura/1.jpg", page.mangas[0].thumbnailUrl)
        assertEquals("/comic/omniscient", page.mangas[1].url)
        assertEquals(3L, page.mangas[1].sourceId)
    }

    @Test
    fun `empty list has no next page`() {
        val page = AsuraHtml.parseMangaList(Jsoup.parse("<html><body></body></html>", baseUrl), manifest, 3L)
        assertTrue(page.mangas.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `details parses meta og image description genres status`() {
        val manga = AsuraHtml.parseDetails(Jsoup.parse(detailsHtml, baseUrl), "/comic/solo-leveling", 3L)
        requireNotNull(manga)
        assertEquals("Solo Leveling", manga.title)
        assertEquals("https://cdn.asura/cover.jpg", manga.thumbnailUrl)
        assertEquals("They say what doesn't kill you makes you stronger.", manga.description)
        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertTrue(manga.initialized)
    }

    @Test
    fun `details without title is null`() {
        assertNull(AsuraHtml.parseDetails(Jsoup.parse("<html><body></body></html>", baseUrl), "/comic/x", 3L))
    }

    @Test
    fun `chapters parse spans dates and fall back to slug name`() {
        val chapters = AsuraHtml.parseChapters(Jsoup.parse(detailsHtml, baseUrl), manifest, now)
        assertEquals(2, chapters.size)
        val first = chapters[0]
        assertEquals("/comic/solo-leveling/chapter-110", first.url)
        assertEquals("Chapter 110", first.name)
        assertEquals(110f, first.chapterNumber)
        assertEquals(now - 2 * 86_400_000L, first.dateUpload)
        val second = chapters[1]
        // Первый span пустой → имя из slug-а, номер тоже из slug-а.
        assertEquals("Chapter 109", second.name)
        assertEquals(109f, second.chapterNumber)
    }

    @Test
    fun `pages come from next data json`() {
        val pages = AsuraHtml.parsePages(Jsoup.parse(chapterHtml, baseUrl), manifest)
        assertEquals(2, pages.size)
        assertEquals("https://tooning.asura/1.png", pages[0].imageUrl)
        assertEquals(0, pages[0].index)
        assertEquals("https://tooning.asura/2.webp", pages[1].imageUrl)
    }

    @Test
    fun `missing or broken next data yields no pages`() {
        assertEquals(0, AsuraHtml.parsePages(Jsoup.parse("<html></html>", baseUrl), manifest).size)
        val broken = """<html><script id="__NEXT_DATA__">{"props":{}}</script></html>"""
        assertEquals(0, AsuraHtml.parsePages(Jsoup.parse(broken, baseUrl), manifest).size)
        val notJson = """<html><script id="__NEXT_DATA__">not json at all</script></html>"""
        assertEquals(0, AsuraHtml.parsePages(Jsoup.parse(notJson, baseUrl), manifest).size)
    }

    @Test
    fun `image arrays with non-string entries are skipped`() {
        val mixed = """
            <html><script id="__NEXT_DATA__">
            {"props":{"images":[1,2],"deep":{"images":["https://x/1.png"]}}}
            </script></html>
        """.trimIndent()
        val pages = AsuraHtml.parsePages(Jsoup.parse(mixed, baseUrl), manifest)
        assertEquals(listOf("https://x/1.png"), pages.map { page -> page.imageUrl })
    }

    @Test
    fun `slug becomes chapter name`() {
        assertEquals("Chapter 110", AsuraHtml.chapterNameFromSlug("/comic/solo-leveling/chapter-110"))
    }

    @Test
    fun `toPath normalizes hrefs`() {
        assertEquals("/comic/x", AsuraHtml.toPath("$baseUrl/comic/x/", baseUrl))
        assertEquals("/comic/x", AsuraHtml.toPath("/comic/x/", baseUrl))
        assertNull(AsuraHtml.toPath("https://other.example/comic/x", baseUrl))
        assertNull(AsuraHtml.toPath("", baseUrl))
    }
}
