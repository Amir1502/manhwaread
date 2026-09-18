package com.manhwaread.source.madara

import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.parseChapterDate
import com.manhwaread.source.api.parseChapterNumber
import com.manhwaread.source.api.parseMangaStatusText
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Чистый jsoup-парсинг разметки темы Madara: без сети, тестируется фикстурами.
// Пустые результаты трактуются источником как сбой разметки (SourceLayoutChanged).
object MadaraHtml {
    private const val CARD_SELECTOR = "div.page-item-detail, div.c-tabs-item__content"
    private const val NEXT_PAGE_SELECTOR = "a.next.page-numbers"
    private const val TITLE_LINK_SELECTOR = "h3 a, h4 a, div.post-title a"
    private const val CHAPTER_ITEM_SELECTOR = "li.wp-manga-chapter"
    private const val CHAPTER_DATE_SELECTOR = "span.chapter-release-date, span.chapter-postdate"
    private const val PAGE_IMAGE_SELECTOR = "div.reading-content img, div.page-break img"
    private const val CHAPTERS_HOLDER_SELECTOR = "div#manga-chapters-holder"

    fun parseMangaList(document: Document, baseUrl: String, sourceId: Long): MangasPage {
        val mangas = document.select(CARD_SELECTOR).mapNotNull { card -> parseCard(card, baseUrl, sourceId) }
        val hasNextPage = document.select(NEXT_PAGE_SELECTOR).isNotEmpty()
        return MangasPage(mangas = mangas, hasNextPage = hasNextPage)
    }

    fun parseDetails(document: Document, mangaUrl: String, sourceId: Long, nsfw: Boolean): SManga? {
        val title = document.selectFirst("div.post-title h1, div.post-title h3")?.text()?.trim().orEmpty()
            .ifBlank { document.selectFirst("h1")?.text()?.trim().orEmpty() }
        if (title.isBlank()) return null
        val author = joinNames(document.select("div.author-content a"))
        val artist = joinNames(document.select("div.artist-content a"))
        val description = document
            .selectFirst("div.description-summary, div.summary__content, div.contenu-summary")
            ?.text()?.trim()?.ifBlank { null }
        val genres = document.select("div.genres-content a")
            .map { element -> element.text().trim() }
            .filter { genre -> genre.isNotBlank() }
        val status = parseMangaStatusText(
            document.selectFirst("div.post-status .summary-content, div.post-status .post-content_item")?.text(),
        )
        val thumbnail = document.selectFirst("div.summary_image img, img.wp-post-image")?.let { image ->
            imageSource(image).ifBlank { null }
        }
        return SManga(
            url = mangaUrl,
            title = title,
            sourceId = sourceId,
            artist = artist,
            author = author,
            description = description,
            genres = genres,
            status = status,
            thumbnailUrl = thumbnail,
            nsfw = nsfw,
            initialized = true,
        )
    }

    fun parseChapters(document: Document, baseUrl: String, nowMillis: Long): List<SChapter> =
        document.select(CHAPTER_ITEM_SELECTOR).mapNotNull { item -> parseChapterItem(item, baseUrl, nowMillis) }

    fun parsePages(document: Document): List<Page> =
        document.select(PAGE_IMAGE_SELECTOR).mapNotNull { image ->
            val source = imageSource(image)
            if (source.isBlank()) null else source
        }.mapIndexed { index, url -> Page(index = index, imageUrl = url) }

    // id тайтла для admin-ajax запроса глав (атрибут data-id держателя).
    fun chaptersHolderId(document: Document): String =
        document.selectFirst(CHAPTERS_HOLDER_SELECTOR)?.attr("data-id").orEmpty()

    private fun parseCard(card: Element, baseUrl: String, sourceId: Long): SManga? {
        val link = card.selectFirst(TITLE_LINK_SELECTOR) ?: return null
        val url = toPath(link.attr("abs:href").ifBlank { link.attr("href") }, baseUrl) ?: return null
        val title = link.text().trim()
            .ifBlank { card.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
        if (title.isBlank()) return null
        val thumbnail = card.selectFirst("img")?.let { image -> imageSource(image).ifBlank { null } }
        return SManga(url = url, title = title, sourceId = sourceId, thumbnailUrl = thumbnail)
    }

    private fun parseChapterItem(item: Element, baseUrl: String, nowMillis: Long): SChapter? {
        val link = item.selectFirst("a") ?: return null
        val url = toPath(link.attr("abs:href").ifBlank { link.attr("href") }, baseUrl) ?: return null
        val name = link.text().trim()
        if (name.isBlank()) return null
        val dateText = item.selectFirst(CHAPTER_DATE_SELECTOR)?.text()
        return SChapter(
            url = url,
            name = name,
            dateUpload = parseChapterDate(dateText, nowMillis),
            chapterNumber = parseChapterNumber(name),
        )
    }

    // Абсолютный или относительный href → путь относительно baseUrl (формат MangaDex).
    internal fun toPath(href: String, baseUrl: String): String? {
        val value = href.trim()
        if (value.isBlank()) return null
        val path = if (value.startsWith("/")) {
            value
        } else {
            val withoutBase = value.removePrefix(baseUrl)
            if (withoutBase.startsWith("/")) withoutBase else null
        } ?: return null
        val trimmed = path.trimEnd('/')
        return trimmed.ifBlank { null }
    }

    // Madara хранит ленивые картинки в data-src; src — фолбэк.
    private fun imageSource(image: Element): String =
        image.attr("data-src").ifBlank { image.attr("src") }.trim()

    private fun joinNames(elements: List<Element>): String? =
        elements.map { element -> element.text().trim() }
            .filter { name -> name.isNotBlank() }
            .joinToString(", ")
            .ifBlank { null }
}
