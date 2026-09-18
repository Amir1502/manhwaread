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

    // Карточки каталога asurascans.com (Astro): <a href="/comics/{slug}"><img alt src>.
    private val listHtml = """
        <html><body>
        <div class="grid">
          <a href="/comics/solo-leveling-6f7fe6eb" class="block relative">
            <img alt="Solo Leveling" src="https://cdn.asurascans.com/asura-images/covers/solo.400.webp">
          </a>
          <a href="$baseUrl/comics/omniscient-6f7fe6eb/">
            <img alt="Omniscient Reader" src="https://cdn.asurascans.com/asura-images/covers/orv.400.webp">
          </a>
          <a href="/comics/solo-leveling-6f7fe6eb/chapter/110"><span>Chapter 110</span></a>
          <a href="/comics/solo-leveling-6f7fe6eb"><img alt="Solo Leveling" src="https://cdn.asurascans.com/asura-images/covers/solo.400.webp"></a>
        </div>
        </body></html>
    """.trimIndent()

    private val detailsHtml = """
        <html><head>
        <meta property="og:image" content="https://cdn.asurascans.com/asura-images/covers/solo.webp">
        <meta name="description" content="They say what doesn't kill you makes you stronger.">
        </head><body>
        <h1 class="text-xl font-semibold">Solo Leveling</h1>
        <span class="text-sm">Ongoing</span>
        <a href="/browse?genres=action">Action</a>
        <a href="/browse?genres=fantasy">Fantasy</a>
        <div class="divide-y">
          <a href="/comics/solo-leveling-6f7fe6eb/chapter/110" class="group flex items-center">
            <span class="font-medium">Chapter <!-- -->110</span><span class="text-sm">2 days ago</span>
          </a>
          <a href="$baseUrl/comics/solo-leveling-6f7fe6eb/chapter/109">
            <span></span><span>March 3, 2024</span>
          </a>
        </div>
        </body></html>
    """.trimIndent()

    // Глава новой раскладки: серверные <img> на CDN, обложка + ленивая страница
    // без src + дубликат (проверка фильтра маркером, data-src и distinct).
    private val chapterHtml = """
        <html><body>
        <img src="https://cdn.asurascans.com/asura-images/covers/solo.400.webp" alt="cover">
        <div class="flex flex-col">
          <div data-page="0">
            <img src="https://cdn.asurascans.com/asura-images/chapters/solo/110/a.webp?v=1"
                 alt="Page 1 - Chapter 110" data-page-index="0" class="w-full block">
          </div>
          <div data-page="1">
            <img data-src="https://cdn.asurascans.com/asura-images/chapters/solo/110/b.webp?v=1"
                 alt="Page 2 - Chapter 110" data-page-index="1">
          </div>
          <div data-page="1">
            <img src="https://cdn.asurascans.com/asura-images/chapters/solo/110/b.webp?v=1" alt="Page 2 dup">
          </div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `list dedupes cards and skips chapter links`() {
        val page = AsuraHtml.parseMangaList(Jsoup.parse(listHtml, baseUrl), manifest, sourceId = 3L)
        assertEquals(2, page.mangas.size)
        assertTrue(page.hasNextPage)
        assertEquals("/comics/solo-leveling-6f7fe6eb", page.mangas[0].url)
        assertEquals("Solo Leveling", page.mangas[0].title)
        assertEquals("https://cdn.asurascans.com/asura-images/covers/solo.400.webp", page.mangas[0].thumbnailUrl)
        assertEquals("/comics/omniscient-6f7fe6eb", page.mangas[1].url)
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
        val manga = AsuraHtml.parseDetails(Jsoup.parse(detailsHtml, baseUrl), "/comics/solo-leveling-6f7fe6eb", 3L)
        requireNotNull(manga)
        assertEquals("Solo Leveling", manga.title)
        assertEquals("https://cdn.asurascans.com/asura-images/covers/solo.webp", manga.thumbnailUrl)
        assertEquals("They say what doesn't kill you makes you stronger.", manga.description)
        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertTrue(manga.initialized)
    }

    @Test
    fun `details without title is null`() {
        assertNull(AsuraHtml.parseDetails(Jsoup.parse("<html><body></body></html>", baseUrl), "/comics/x", 3L))
    }

    @Test
    fun `chapters parse spans dates and fall back to slug name`() {
        val chapters = AsuraHtml.parseChapters(Jsoup.parse(detailsHtml, baseUrl), manifest, now)
        assertEquals(2, chapters.size)
        val first = chapters[0]
        assertEquals("/comics/solo-leveling-6f7fe6eb/chapter/110", first.url)
        assertEquals("Chapter 110", first.name)
        assertEquals(110f, first.chapterNumber)
        assertEquals(now - 2 * 86_400_000L, first.dateUpload)
        val second = chapters[1]
        // Первый span пустой → имя из последнего сегмента url (/chapter/109).
        assertEquals("Chapter 109", second.name)
        assertEquals(109f, second.chapterNumber)
    }

    @Test
    fun `pages come from chapter images`() {
        val pages = AsuraHtml.parsePages(Jsoup.parse(chapterHtml, baseUrl), manifest)
        assertEquals(2, pages.size)
        assertEquals("https://cdn.asurascans.com/asura-images/chapters/solo/110/a.webp?v=1", pages[0].imageUrl)
        assertEquals(0, pages[0].index)
        assertEquals("https://cdn.asurascans.com/asura-images/chapters/solo/110/b.webp?v=1", pages[1].imageUrl)
        assertEquals(1, pages[1].index)
    }

    @Test
    fun `page without chapter images yields no pages`() {
        assertEquals(0, AsuraHtml.parsePages(Jsoup.parse("<html></html>", baseUrl), manifest).size)
        val coversOnly = """
            <html><body>
            <img src="https://cdn.asurascans.com/asura-images/covers/solo.400.webp" alt="cover">
            </body></html>
        """.trimIndent()
        assertEquals(0, AsuraHtml.parsePages(Jsoup.parse(coversOnly, baseUrl), manifest).size)
    }

    @Test
    fun `slug becomes chapter name`() {
        assertEquals("Chapter 110", AsuraHtml.chapterNameFromSlug("/comics/solo-leveling/chapter/110"))
        assertEquals("Chapter 110.5", AsuraHtml.chapterNameFromSlug("/comics/solo-leveling/chapter/110.5"))
        assertEquals("Chapter 110", AsuraHtml.chapterNameFromSlug("/comic/solo-leveling/chapter-110"))
    }

    @Test
    fun `toPath normalizes hrefs`() {
        assertEquals("/comics/x", AsuraHtml.toPath("$baseUrl/comics/x/", baseUrl))
        assertEquals("/comics/x", AsuraHtml.toPath("/comics/x/", baseUrl))
        assertNull(AsuraHtml.toPath("https://other.example/comics/x", baseUrl))
        assertNull(AsuraHtml.toPath("", baseUrl))
    }
}
