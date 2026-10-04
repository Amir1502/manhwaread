package com.manhwaread.source.mangamir

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaMirParserTest {
    private fun loadFixture(name: String): Document {
        val stream = javaClass.classLoader.getResourceAsStream("mangamir/$name")
            ?: error("Fixture not found: mangamir/$name")
        val html = stream.bufferedReader().use { it.readText() }
        return Jsoup.parse(html, "https://mangamir.com")
    }

    @Test
    fun `parseCatalog popular page 1 extracts 3 cards and hasNextPage true`() {
        val doc = loadFixture("catalog_popular_page1.html")
        val result = MangaMirParser.parseCatalog(doc)

        assertEquals(3, result.items.size)
        assertTrue(result.hasNextPage)

        val first = result.items[0]
        assertEquals("ot-goblina-k-bogu-goblinov-a4izl1", first.slug)
        assertEquals("От гоблина к богу гоблинов", first.title)
        assertEquals("Маньхуа", first.type)
        assertEquals(MmStatus.ONGOING, first.status)
        assertEquals(
            "https://img.mangamir.com/posters/1119/DeURy41N39rT2r2hKzyrwg1Wl8ETBOixDcsTSRiN_md.jpeg",
            first.coverUrl,
        )

        val second = result.items[1]
        assertEquals("nachalo-posle-konca-zx5b2g", second.slug)
        assertEquals("Начало после конца", second.title)
        assertEquals("OEL Манга", second.type)
        assertEquals(MmStatus.ONGOING, second.status)

        val third = result.items[2]
        assertEquals("paladin-urovnya-sss-prevoshodyashchiy-zdravyy-smysl", third.slug)
        assertEquals("Паладин уровня SSS, превосходящий здравый смысл", third.title)
        assertEquals("Маньхуа", third.type)
        assertEquals(MmStatus.ONGOING, third.status)
    }

    @Test
    fun `parseCatalog search korol extracts 3 cards`() {
        val doc = loadFixture("catalog_search_korol.html")
        val result = MangaMirParser.parseCatalog(doc)

        assertEquals(3, result.items.size)
        assertTrue(result.hasNextPage)
        assertEquals("Король демонов, поднимающий свой уровень боевыми искусствами", result.items[0].title)
        assertEquals("Судный День: Я, Король вирусов", result.items[1].title)
        assertEquals("Вернувшись с властью короля", result.items[2].title)
    }

    @Test
    fun `parseCatalog last page extracts 1 card and hasNextPage false`() {
        val doc = loadFixture("catalog_last_page.html")
        val result = MangaMirParser.parseCatalog(doc)

        assertEquals(1, result.items.size)
        assertFalse(result.hasNextPage)

        val item = result.items[0]
        assertEquals("tishe-edesh-dalshe-budesh", item.slug)
        assertEquals("Тише едешь — дальше будешь", item.title)
        assertEquals("Манга", item.type)
        assertEquals(MmStatus.COMPLETED, item.status)
    }

    @Test
    fun `parseCatalog empty page returns empty list and hasNextPage false`() {
        val doc = loadFixture("catalog_empty.html")
        val result = MangaMirParser.parseCatalog(doc)

        assertTrue(result.items.isEmpty())
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `parseDetails with toc extracts complete title information`() {
        val doc = loadFixture("details_toc.html")
        val details = MangaMirParser.parseDetails(doc, "korol-mecha")

        assertEquals("Король меча", details.title)
        assertEquals("https://img.mangamir.com/posters/1126/M4A6Kp6dEaHUyZ9Xmg9dr5CvGqLbGU6Rgbf9Cppf.jpeg", details.coverUrl)
        assertEquals(MmStatus.ONGOING, details.status)
        assertEquals("Манхва", details.type)
        assertEquals("16+", details.ageRating)
        assertEquals(9.52, details.rating)
        assertEquals(23, details.ratingCount)
        assertEquals(32, details.genres.size)
        assertNotNull(details.description)
        assertTrue(details.description!!.contains("Рю Хан-Бин попадает в иной мир"))
        assertTrue(details.description!!.contains("\n"))

        assertEquals(3, details.related.size)
        assertEquals("kak-vyzhit-v-akademii", details.related[0].slug)
        assertEquals("mladshiy-syn-mechnika", details.related[1].slug)
        assertEquals("nachalo-posle-konca-zx5b2g", details.related[2].slug)
    }

    @Test
    fun `parseChapters with toc extracts 6 valid chapters excluding forecast`() {
        val doc = loadFixture("details_toc.html")
        val chapters = MangaMirParser.parseChapters(doc)

        assertEquals(6, chapters.size)

        val ch302 = chapters[0]
        assertEquals("Том 7 Глава 302", ch302.name)
        assertEquals("/manga/korol-mecha/tom-7-glava-302", ch302.url)
        assertEquals(7, ch302.volume)
        assertEquals("302", ch302.numberText)
        assertEquals(302.0, ch302.number)
        assertEquals(300, ch302.position)
        assertEquals(1791079203000L, ch302.uploadedAtMillis)

        val ch300Point1 = chapters[2]
        assertEquals("Том 7 Глава 300.1", ch300Point1.name)
        assertEquals("/manga/korol-mecha/tom-7-glava-300-1", ch300Point1.url)
        assertEquals(7, ch300Point1.volume)
        assertEquals("300.1", ch300Point1.numberText)
        assertEquals(300.1, ch300Point1.number)
        assertEquals(298, ch300Point1.position)

        val ch0 = chapters[5]
        assertEquals("Том 1 Глава 0", ch0.name)
        assertEquals(1, ch0.volume)
        assertEquals("0", ch0.numberText)
        assertEquals(0.0, ch0.number)
        assertEquals(1, ch0.position)
    }

    @Test
    fun `parseDetails broken jsonld falls back to DOM without exception`() {
        val doc = loadFixture("details_broken_jsonld.html")
        val details = MangaMirParser.parseDetails(doc, "korol-mecha")

        assertEquals("Король меча", details.title)
        assertNotNull(details.coverUrl)
        assertEquals(4, details.genres.size)
        assertNull(details.rating)

        val chapters = MangaMirParser.parseChapters(doc)
        assertEquals(6, chapters.size)
        assertEquals("Том 7 Глава 302", chapters[0].name)
    }

    @Test
    fun `parsePages extracts ordered chapter pages from reader container`() {
        val doc = loadFixture("chapter_reader.html")
        val pages = MangaMirParser.parsePages(doc)

        assertEquals(3, pages.size)
        assertEquals(0, pages[0].index)
        assertEquals(
            "https://img.mangamir.com/pages/1126/6476abadfff292803a967a732282acbbcfbb4558.webp",
            pages[0].imageUrl,
        )
        assertEquals(1448, pages[0].width)
        assertEquals(2000, pages[0].height)

        assertEquals(1, pages[1].index)
        assertEquals(
            "https://img.mangamir.com/pages/1126/f3d87ca14fa3acb4292d0b976a99e9bf5f411477.webp",
            pages[1].imageUrl,
        )

        assertEquals(2, pages[2].index)
        assertEquals(
            "https://img.mangamir.com/pages/1126/9ea74e03605c8a393bd7200cfd044d592b442f5d.webp",
            pages[2].imageUrl,
        )
    }
}
