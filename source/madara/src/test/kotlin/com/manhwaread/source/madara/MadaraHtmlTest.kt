package com.manhwaread.source.madara

import com.manhwaread.source.api.MangaStatus
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MadaraHtmlTest {
    private val baseUrl = "https://site.example"
    private val now = 1_700_000_000_000L

    private val listHtml = """
        <html><body>
        <div class="c-tabs-item__content">
          <a href="$baseUrl/manga/solo-leveling/" title="Solo Leveling">
            <img class="img-responsive" data-src="https://cdn.example/cover.jpg" alt="Solo Leveling">
          </a>
          <div class="post-title">
            <h3 class="h5"><a href="$baseUrl/manga/solo-leveling/">Solo Leveling</a></h3>
          </div>
        </div>
        <div class="page-item-detail">
          <div class="post-title"><h3 class="h4"><a href="/manga/second-life/">Second Life Ranker</a></h3></div>
          <img src="https://cdn.example/second.jpg">
        </div>
        <a class="next page-numbers" href="?page=3">Next</a>
        </body></html>
    """.trimIndent()

    private val detailsHtml = """
        <html><body>
        <div class="profile-manga">
          <div class="summary_image"><img class="wp-post-image" data-src="https://cdn.example/cover.jpg"></div>
          <div class="post-title"><h1>Solo Leveling</h1></div>
          <div class="post-status"><div class="post-content_item"><div class="summary-content">OnGoing</div></div></div>
          <div class="author-content"><a>Chugong</a></div>
          <div class="artist-content"><a>DUBU</a><a>Disciples</a></div>
          <div class="genres-content"><a>Action</a><a>Fantasy</a></div>
          <div class="description-summary"><div class="contenu-summary">After being slaughtered by monsters.</div></div>
        </div>
        </body></html>
    """.trimIndent()

    private val chaptersHtml = """
        <html><body>
        <div id="manga-chapters-holder" data-id="12345"></div>
        <ul class="version-chap">
          <li class="wp-manga-chapter">
            <a href="$baseUrl/manga/solo/chapter-12/">Chapter 12</a>
            <span class="chapter-release-date">5 hours ago</span>
          </li>
          <li class="wp-manga-chapter free-chap">
            <a href="/manga/solo/chapter-11/">Chapter 11</a>
            <span class="chapter-release-date">March 3, 2024</span>
          </li>
          <li class="wp-manga-chapter"><a href="/manga/solo/no-title/"></a></li>
        </ul>
        </body></html>
    """.trimIndent()

    private val pagesHtml = """
        <html><body>
        <div class="reading-content">
          <img class="wp-manga-chapter-img" data-src=" https://cdn.example/p1.png ">
          <img class="wp-manga-chapter-img" src="https://cdn.example/p2.png">
          <img class="wp-manga-chapter-img">
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `list page parses cards and pagination`() {
        val page = MadaraHtml.parseMangaList(Jsoup.parse(listHtml, baseUrl), baseUrl, sourceId = 2L)
        assertEquals(2, page.mangas.size)
        assertTrue(page.hasNextPage)
        val first = page.mangas[0]
        assertEquals("/manga/solo-leveling", first.url)
        assertEquals("Solo Leveling", first.title)
        assertEquals("https://cdn.example/cover.jpg", first.thumbnailUrl)
        assertEquals(2L, first.sourceId)
        val second = page.mangas[1]
        assertEquals("/manga/second-life", second.url)
        assertEquals("https://cdn.example/second.jpg", second.thumbnailUrl)
    }

    @Test
    fun `list page without next link has no next page`() {
        val html = listHtml.replace("""<a class="next page-numbers" href="?page=3">Next</a>""", "")
        val page = MadaraHtml.parseMangaList(Jsoup.parse(html, baseUrl), baseUrl, 2L)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `empty list page yields no mangas`() {
        val page = MadaraHtml.parseMangaList(Jsoup.parse("<html><body></body></html>", baseUrl), baseUrl, 2L)
        assertTrue(page.mangas.isEmpty())
        assertFalse(page.hasNextPage)
    }

    @Test
    fun `details parses every field`() {
        val manga = MadaraHtml.parseDetails(Jsoup.parse(detailsHtml, baseUrl), "/manga/solo-leveling", 2L, nsfw = false)
        requireNotNull(manga)
        assertEquals("Solo Leveling", manga.title)
        assertEquals("Chugong", manga.author)
        assertEquals("DUBU, Disciples", manga.artist)
        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals("After being slaughtered by monsters.", manga.description)
        assertEquals("https://cdn.example/cover.jpg", manga.thumbnailUrl)
        assertTrue(manga.initialized)
        assertEquals("/manga/solo-leveling", manga.url)
    }

    @Test
    fun `details without title is null`() {
        assertNull(MadaraHtml.parseDetails(Jsoup.parse("<html><body></body></html>", baseUrl), "/manga/x", 2L, false))
    }

    @Test
    fun `chapters parse names urls dates and numbers`() {
        val chapters = MadaraHtml.parseChapters(Jsoup.parse(chaptersHtml, baseUrl), baseUrl, now)
        assertEquals(2, chapters.size)
        val first = chapters[0]
        assertEquals("/manga/solo/chapter-12", first.url)
        assertEquals("Chapter 12", first.name)
        assertEquals(12f, first.chapterNumber)
        assertEquals(now - 5 * 3_600_000L, first.dateUpload)
        assertEquals(11f, chapters[1].chapterNumber)
    }

    @Test
    fun `chapters holder id extracted for ajax fallback`() {
        assertEquals("12345", MadaraHtml.chaptersHolderId(Jsoup.parse(chaptersHtml, baseUrl)))
        assertEquals("", MadaraHtml.chaptersHolderId(Jsoup.parse("<html></html>", baseUrl)))
    }

    @Test
    fun `pages take data-src first and skip blanks`() {
        val pages = MadaraHtml.parsePages(Jsoup.parse(pagesHtml, baseUrl))
        assertEquals(2, pages.size)
        assertEquals("https://cdn.example/p1.png", pages[0].imageUrl)
        assertEquals(0, pages[0].index)
        assertEquals("https://cdn.example/p2.png", pages[1].imageUrl)
        assertEquals(1, pages[1].index)
    }

    @Test
    fun `toPath normalizes absolute and relative hrefs`() {
        assertEquals("/manga/x", MadaraHtml.toPath("$baseUrl/manga/x/", baseUrl))
        assertEquals("/manga/x", MadaraHtml.toPath("/manga/x/", baseUrl))
        assertNull(MadaraHtml.toPath("https://other.example/manga/x/", baseUrl))
        assertNull(MadaraHtml.toPath("", baseUrl))
        assertNull(MadaraHtml.toPath("/", baseUrl))
    }

    @Test
    fun `manga18fx config carries age gate`() {
        val config = manga18fxConfig()
        assertEquals("Manga18fx", config.name)
        assertEquals("https://manga18fx.com", config.baseUrl)
        assertTrue(config.isNsfw)
        assertEquals("age_verified=1", config.adultCookie)
        assertEquals(2L, config.id)
    }
}
