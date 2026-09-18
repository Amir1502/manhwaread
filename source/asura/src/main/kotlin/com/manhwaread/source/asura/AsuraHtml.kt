package com.manhwaread.source.asura

import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.parseChapterDate
import com.manhwaread.source.api.parseChapterNumber
import com.manhwaread.source.api.parseMangaStatusText
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Чистый jsoup-парсинг Asura Scans (asurascans.com, сборка Astro). Без сети —
// тестируется фикстурами; пустые результаты источник трактует как сбой
// разметки (SourceLayoutChanged «update the manifest»).
object AsuraHtml {
    fun parseMangaList(document: Document, manifest: AsuraManifest, sourceId: Long): MangasPage {
        val seen = linkedSetOf<String>()
        // Все ссылки страницы нормализуются в путь; карточка тайтла — путь
        // с префиксом comicLinkPrefix, не содержащий chapterLinkInfix.
        val mangas = document.select("a[href]")
            .mapNotNull { link -> parseComicCard(link, manifest, sourceId, seen) }
        // /browse пагинируется сервером: пока карточки есть — есть и следующая
        // страница; на переполнении сайт отдаёт пустую сетку.
        return MangasPage(mangas = mangas, hasNextPage = mangas.isNotEmpty())
    }

    private fun parseComicCard(
        link: Element,
        manifest: AsuraManifest,
        sourceId: Long,
        seen: MutableSet<String>,
    ): SManga? {
        val url = comicCardUrl(link, manifest, seen) ?: return null
        val title = cardTitle(link)
        if (title.isBlank()) return null
        val thumbnail = link.selectFirst("img")?.let { image -> imageSource(image).ifBlank { null } }
        return SManga(url = url, title = title, sourceId = sourceId, thumbnailUrl = thumbnail)
    }

    // Путь карточки, если ссылка ведёт на тайтл (не на главу) и ещё не встречалась.
    private fun comicCardUrl(link: Element, manifest: AsuraManifest, seen: MutableSet<String>): String? {
        val url = toPath(link.attr("href"), manifest.baseUrl) ?: return null
        if (!url.startsWith(manifest.comicLinkPrefix)) return null
        if (url.contains(manifest.chapterLinkInfix)) return null
        return url.takeIf { seen.add(url) }
    }

    fun parseDetails(
        document: Document,
        mangaUrl: String,
        sourceId: Long,
    ): SManga? {
        val title = document.selectFirst("h1")?.text()?.trim().orEmpty()
            .ifBlank { metaContent(document, "og:title").orEmpty() }
        if (title.isBlank()) return null
        val description = metaContent(document, "description")?.ifBlank { null }
        val thumbnail = metaContent(document, "og:image")?.ifBlank { null }
            ?: document.selectFirst("img")?.let { image -> imageSource(image).ifBlank { null } }
        val genres = document.select("a[href*=genres]")
            .map { element -> element.text().trim() }
            .filter { genre -> genre.isNotBlank() }
        val status = document.select("span")
            .map { element -> parseMangaStatusText(element.text()) }
            .firstOrNull { parsed -> parsed != MangaStatus.UNKNOWN }
            ?: MangaStatus.UNKNOWN
        return SManga(
            url = mangaUrl,
            title = title,
            sourceId = sourceId,
            description = description,
            genres = genres,
            status = status,
            thumbnailUrl = thumbnail,
            initialized = true,
        )
    }

    fun parseChapters(document: Document, manifest: AsuraManifest, nowMillis: Long): List<SChapter> =
        document.select("a[href*=${manifest.chapterLinkInfix}]").mapNotNull { link ->
            val href = link.attr("abs:href").ifBlank { link.attr("href") }
            val url = toPath(href, manifest.baseUrl) ?: return@mapNotNull null
            val spans = link.select("span")
            // Имя: первый span; если он пуст — из последнего сегмента url
            // (/chapter/110 → Chapter 110). Текст ссылки целиком — фолбэк
            // только когда span-ов нет вовсе (иначе в имя попала бы дата).
            val name = if (spans.isEmpty()) {
                link.text().trim().ifBlank { chapterNameFromSlug(url) }
            } else {
                spans[0].text().trim().ifBlank { chapterNameFromSlug(url) }
            }
            if (name.isBlank()) return@mapNotNull null
            val dateText = spans.getOrNull(1)?.text()
            SChapter(
                url = url,
                name = name,
                dateUpload = parseChapterDate(dateText, nowMillis),
                chapterNumber = parseChapterNumber(name).takeIf { number -> number >= 0f }
                    ?: parseChapterNumber(chapterNameFromSlug(url)),
            )
        }

    // Страницы главы — серверный рендер Astro: <img> со src (или ленивым
    // data-src) на CDN-каталог главы; обложки отсекаются маркером манифеста.
    fun parsePages(document: Document, manifest: AsuraManifest): List<Page> =
        document.select("img")
            .mapNotNull { image -> imageSource(image).ifBlank { null } }
            .filter { source -> manifest.chapterImageSrcMarker in source }
            .distinct()
            .mapIndexed { index, url -> Page(index = index, imageUrl = url) }

    private fun cardTitle(link: Element): String =
        link.selectFirst("img")?.attr("alt")?.trim().orEmpty()
            .ifBlank { link.selectFirst("h1, h2, h3, h4, h5")?.text()?.trim().orEmpty() }
            .ifBlank { link.text().trim() }

    private fun imageSource(image: Element): String =
        image.attr("src").ifBlank { image.attr("data-src") }.trim()

    private fun metaContent(document: Document, key: String): String? =
        document.selectFirst("meta[property=$key], meta[name=$key]")?.attr("content")?.trim()

    // Новая раскладка "/comics/solo/chapter/110" → "Chapter 110";
    // старая "/comic/solo/chapter-110" → "Chapter 110".
    internal fun chapterNameFromSlug(url: String): String {
        val slug = url.substringAfterLast('/', "")
        if (slug.replace(',', '.').toDoubleOrNull() != null) return "Chapter $slug"
        return slug.replace('-', ' ')
            .replaceFirstChar { first -> first.uppercaseChar() }
            .ifBlank { url }
    }

    // Абсолютный или относительный href → путь относительно baseUrl.
    internal fun toPath(href: String, baseUrl: String): String? {
        val value = href.trim()
        if (value.isBlank()) return null
        val path = if (value.startsWith("/")) {
            value
        } else {
            val withoutBase = value.removePrefix(baseUrl)
            if (withoutBase.startsWith("/")) withoutBase else null
        } ?: return null
        return path.trimEnd('/').ifBlank { null }
    }
}
