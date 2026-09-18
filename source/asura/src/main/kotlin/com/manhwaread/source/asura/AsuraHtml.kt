package com.manhwaread.source.asura

import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.parseChapterDate
import com.manhwaread.source.api.parseChapterNumber
import com.manhwaread.source.api.parseMangaStatusText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Чистый jsoup/JSON-парсинг Asura Scans (Next.js-вёрстка). Без сети —
// тестируется фикстурами; пустые результаты источник трактует как сбой
// разметки (SourceLayoutChanged «update the manifest»).
object AsuraHtml {
    fun parseMangaList(document: Document, manifest: AsuraManifest, sourceId: Long): MangasPage {
        val seen = linkedSetOf<String>()
        // Все ссылки страницы нормализуются в путь; карточка тайтла — путь
        // с префиксом comicLinkPrefix, не содержащий chapterLinkInfix.
        val mangas = document.select("a[href]")
            .mapNotNull { link -> parseComicCard(link, manifest, sourceId, seen) }
        // У Asura подгрузка страниц бесконечная: пока карточки есть — есть и следующая страница.
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
            // Имя: первый span; если он пуст — из slug-а (chapter-110 → Chapter 110).
            // Текст ссылки целиком — фолбэк только когда span-ов нет вовсе
            // (иначе в имя попала бы дата из второго span).
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

    // Страницы главы — из JSON скрипта __NEXT_DATA__ (Next.js payload).
    fun parsePages(document: Document, manifest: AsuraManifest): List<Page> {
        val script = document.getElementById(manifest.nextDataElementId) ?: return emptyList()
        val payload = script.data().ifBlank { script.html() }
        val root = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return emptyList()
        val images = findImageArray(root, manifest.imageContainerKeys) ?: return emptyList()
        return images.mapIndexed { index, url -> Page(index = index, imageUrl = url) }
    }

    // Обход JSON в глубину: первый массив строк под ключом из [keys].
    private fun findImageArray(element: JsonElement, keys: Set<String>): List<String>? =
        when (element) {
            is JsonObject -> findImageArrayInObject(element, keys)
            is JsonArray -> findImageArrayInArray(element, keys)
            is JsonPrimitive -> null
        }

    private fun findImageArrayInObject(obj: JsonObject, keys: Set<String>): List<String>? {
        for ((key, value) in obj) {
            val direct = directImageArray(key, value, keys)
            if (direct != null) return direct
        }
        for ((_, value) in obj) {
            val nested = findImageArray(value, keys)
            if (nested != null) return nested
        }
        return null
    }

    private fun findImageArrayInArray(array: JsonArray, keys: Set<String>): List<String>? {
        for (item in array) {
            val nested = findImageArray(item, keys)
            if (nested != null) return nested
        }
        return null
    }

    // Массив подходит, если он непустой и целиком из строк (иначе это не картинки).
    private fun directImageArray(key: String, value: JsonElement, keys: Set<String>): List<String>? {
        if (key !in keys || value !is JsonArray) return null
        val urls = value.mapNotNull { item ->
            (item as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
        }
        return urls.takeIf { list -> list.isNotEmpty() && list.size == value.size }
    }

    private fun cardTitle(link: Element): String =
        link.selectFirst("img")?.attr("alt")?.trim().orEmpty()
            .ifBlank { link.selectFirst("h1, h2, h3, h4, h5")?.text()?.trim().orEmpty() }
            .ifBlank { link.text().trim() }

    private fun imageSource(image: Element): String =
        image.attr("src").ifBlank { image.attr("data-src") }.trim()

    private fun metaContent(document: Document, key: String): String? =
        document.selectFirst("meta[property=$key], meta[name=$key]")?.attr("content")?.trim()

    // "/comic/solo-leveling/chapter-110" → "Chapter 110".
    internal fun chapterNameFromSlug(url: String): String {
        val slug = url.substringAfterLast('/', "")
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
