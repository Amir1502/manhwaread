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
    fun `manga18fx config targets manga stream layout`() {
        val config = manga18fxConfig()
        assertEquals("Manga18fx", config.name)
        assertEquals("https://manga18fx.com", config.baseUrl)
        assertTrue(config.isNsfw)
        assertEquals("age_verified=1", config.adultCookie)
        assertEquals(2L, config.id)
        // Новая раскладка (2025): /hot-manga, корневая пагинация, /search?q=.
        assertEquals("/hot-manga?page={page}", config.popularPathTemplate)
        assertEquals("/page/{page}", config.latestPathTemplate)
        assertEquals("/search?page={page}", config.searchPathTemplate)
        assertEquals("q", config.searchQueryParam)
        assertTrue(config.searchExtraParams.isEmpty())
    }

    // Фикстуры темы MangaStream (manga18fx c 2025): карточки bsx-item/hot-item,
    // пагинация blog-pager li.next, главы li.a-h с датой «04 Sep 26».
    private val mangaStreamListHtml = """
        <html><body>
        <div class="listupd">
          <div class="page-item">
            <div class="bsx-item">
              <div class="thumb-manga">
                <a href="/manga/secret-class-01">
                  <div class="adult-badges">18+</div>
                  <img data-src="https://manga18fx.com/webtoon/secret-classm.jpg"
                       src="https://manga18fx.com/webtoon/secret-classm.jpg" alt="Secret Class">
                </a>
              </div>
              <div class="bigor-manga">
                <h3 class="tt"><a href="/manga/secret-class-01">Secret Class</a></h3>
              </div>
            </div>
          </div>
          <div class="hot-item mycover">
            <a href="/manga/opentalk" title="OpenTalk">
              <div class="chapter-badges">Chapter. 8</div>
              <img src="https://manga18fx.com/webtoon/opentalkm.jpg" alt="OpenTalk">
              <div class="caption"><h3>OpenTalk</h3></div>
            </a>
          </div>
        </div>
        <div class="blog-pager" id="blog-pager">
          <ul class="pagination">
            <li class="prev disabled"><span>&laquo;</span></li>
            <li class="active"><a href="/page/1" data-page="0">1</a></li>
            <li class="next"><a href="/page/2" data-page="1">&raquo;</a></li>
          </ul>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `manga stream list parses both card types and next page`() {
        val page = MadaraHtml.parseMangaList(Jsoup.parse(mangaStreamListHtml, baseUrl), baseUrl, 2L)
        assertEquals(2, page.mangas.size)
        assertTrue(page.hasNextPage)
        val first = page.mangas[0]
        assertEquals("/manga/secret-class-01", first.url)
        assertEquals("Secret Class", first.title)
        assertEquals("https://manga18fx.com/webtoon/secret-classm.jpg", first.thumbnailUrl)
        val second = page.mangas[1]
        assertEquals("/manga/opentalk", second.url)
        assertEquals("OpenTalk", second.title)
        assertEquals("https://manga18fx.com/webtoon/opentalkm.jpg", second.thumbnailUrl)
    }

    @Test
    fun `manga stream last page has no next link`() {
        val html = mangaStreamListHtml.replace(
            """<li class="next"><a href="/page/2" data-page="1">&raquo;</a></li>""",
            """<li class="next disabled"><span>&raquo;</span></li>""",
        )
        val page = MadaraHtml.parseMangaList(Jsoup.parse(html, baseUrl), baseUrl, 2L)
        assertEquals(2, page.mangas.size)
        assertFalse(page.hasNextPage)
    }

    private val mangaStreamTitleHtml = """
        <html><body>
        <div class="post-title"><h1>Secret Class</h1></div>
        <div class="tab-summary">
          <div class="summary_image">
            <a href="/manga/secret-class-01"><img class="img-loading"
              data-src="https://manga18fx.com/webtoon/secret-classm.jpg" alt="Secret Class"></a>
          </div>
          <div class="post-status"><div class="post-content_item">
            <div class="summary-heading"><h5>Release</h5></div>
            <div class="summary-content" style="text-align: right">Ongoing</div>
          </div></div>
          <div class="author-content"><a>Author Name</a></div>
          <div class="artist-content"><a>Artist Name</a></div>
          <div class="genres-content"><a>Comedy</a><a>Drama</a></div>
        </div>
        <div class="panel-story-description">
          <h2 class="manga-panel-title">Summary</h2>
          <div class="dsct"><p>Secret Class is about a wife of two.</p></div>
        </div>
        <ul class="main-chap">
          <li class="a-h">
            <a class="chapter-name text-nowrap" href="/manga/secret-class-01/chapter-317"
               title="Secret Class Chapter 317">Chapter 317</a>
            <span class="chapter-time text-nowrap" title="">04 Sep 26</span>
          </li>
          <li class="a-h">
            <a class="chapter-name text-nowrap" href="/manga/secret-class-01/chapter-316-5">Chapter 316.5</a>
            <span class="chapter-time text-nowrap" title="">17 Sep 26</span>
          </li>
        </ul>
        </body></html>
    """.trimIndent()

    @Test
    fun `manga stream details parses summary and status`() {
        val manga = MadaraHtml.parseDetails(
            Jsoup.parse(mangaStreamTitleHtml, baseUrl),
            "/manga/secret-class-01",
            2L,
            nsfw = true,
        )
        requireNotNull(manga)
        assertEquals("Secret Class", manga.title)
        assertEquals("Secret Class is about a wife of two.", manga.description)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(listOf("Comedy", "Drama"), manga.genres)
        assertEquals("Author Name", manga.author)
        assertEquals("https://manga18fx.com/webtoon/secret-classm.jpg", manga.thumbnailUrl)
        assertTrue(manga.nsfw)
    }

    @Test
    fun `manga stream chapters parse with short dates`() {
        val chapters = MadaraHtml.parseChapters(Jsoup.parse(mangaStreamTitleHtml, baseUrl), baseUrl, now)
        assertEquals(2, chapters.size)
        val first = chapters[0]
        assertEquals("/manga/secret-class-01/chapter-317", first.url)
        assertEquals("Chapter 317", first.name)
        assertEquals(317f, first.chapterNumber)
        val expected = java.time.LocalDate.of(2026, 9, 4)
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, first.dateUpload)
        assertEquals(316.5f, chapters[1].chapterNumber)
    }

    @Test
    fun `manga stream pages parse from page-break`() {
        val html = """
            <html><body>
            <div class="page-break">
              <img class="loading p1" data-src="https://img01.manga18fx.com/uploads/264/1/1-001.jpg"
                   src="https://img01.manga18fx.com/uploads/264/1/1-001.jpg" loading="lazy"
                   alt="Secret Class - Chapter 1">
            </div>
            </body></html>
        """.trimIndent()
        val pages = MadaraHtml.parsePages(Jsoup.parse(html, baseUrl))
        assertEquals(1, pages.size)
        assertEquals("https://img01.manga18fx.com/uploads/264/1/1-001.jpg", pages[0].imageUrl)
    }
}
