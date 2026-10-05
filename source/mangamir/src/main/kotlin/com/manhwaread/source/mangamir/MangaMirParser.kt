package com.manhwaread.source.mangamir

import com.manhwaread.core.model.ChapterNumberParser
import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.MangaType
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.PageStatus
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.OffsetDateTime

const val MANGAMIR_SOURCE_ID = 4001L

internal enum class MmStatus { ONGOING, COMPLETED, UNKNOWN }

internal data class MmCard(
    val slug: String,
    val title: String,
    val coverUrl: String?,
    val type: String?,
    val status: MmStatus,
)

internal data class MmCatalogPage(val items: List<MmCard>, val hasNextPage: Boolean)

internal data class MmDetails(
    val slug: String,
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val genres: List<String>,
    val tags: List<String>,
    val type: String?,
    val ageRating: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val status: MmStatus,
    val related: List<MmCard>,
)

internal data class MmChapter(
    val url: String,
    val name: String,
    val volume: Int?,
    val numberText: String?,
    val number: Double?,
    val position: Int,
    val uploadedAtMillis: Long?,
)

internal data class MmPage(val index: Int, val imageUrl: String, val width: Int?, val height: Int?)

internal fun MmStatus.toMangaStatus(): MangaStatus = when (this) {
    MmStatus.ONGOING -> MangaStatus.ONGOING
    MmStatus.COMPLETED -> MangaStatus.COMPLETED
    MmStatus.UNKNOWN -> MangaStatus.UNKNOWN
}

internal fun MmCard.toSManga(sourceId: Long): SManga = SManga(
    url = "/manga/$slug",
    title = title,
    sourceId = sourceId,
    thumbnailUrl = coverUrl,
    status = status.toMangaStatus(),
    initialized = false,
    type = MangaType.fromString(type),
)

internal fun MmDetails.toSManga(sourceId: Long, originalUrl: String): SManga = SManga(
    url = originalUrl.ifBlank { "/manga/$slug" },
    title = title,
    sourceId = sourceId,
    author = null,
    artist = null,
    description = description,
    genres = genres,
    status = status.toMangaStatus(),
    thumbnailUrl = coverUrl,
    nsfw = ageRating == "18+",
    initialized = true,
    rating = rating?.toFloat(),
    type = MangaType.fromString(type),
    ageRating = ageRating,
)

internal fun MmChapter.toSChapter(): SChapter = SChapter(
    url = url,
    name = name,
    dateUpload = uploadedAtMillis ?: 0L,
    chapterNumber = number?.toFloat() ?: -1f,
    scanlator = null,
    read = false,
)

internal fun MmPage.toPage(): Page = Page(
    index = index,
    imageUrl = imageUrl,
    status = PageStatus.READY,
)

internal object MangaMirParser {
    private val VOLUME_REGEX =
        Regex("""(?iu)(?:^|[^\p{L}\p{N}])(?:том|vol|volume)\s*\.?\s*(\d+)(?:[^\p{L}\p{N}]|$)""")
    private val CHAPTER_REGEX =
        Regex("""(?iu)(?:^|[^\p{L}\p{N}])(?:глава|гл|chapter|ch)\s*\.?\s*([0-9]+(?:[.,][0-9]+)?)(?:[^\p{L}\p{N}]|$)""")
    private val NUMBER_REGEX = Regex("""([0-9]+(?:[.,][0-9]+)?)""")
    private val AGE_RATING_REGEX = Regex("""^\d{1,2}\+$""")

    fun parseCatalog(doc: Document): MmCatalogPage {
        val anchors = doc.select("a[data-card-link-type=\"poster\"]")
        val cards = mutableListOf<MmCard>()
        val seenSlugs = mutableSetOf<String>()

        for (anchor in anchors) {
            val href = anchor.attr("href")
            val slug = MangaMirUrls.slugFromMangaUrl(href)
            if (slug.isBlank() || !seenSlugs.add(slug)) continue

            val title = extractCardTitle(anchor)
            val rawCover = anchor.selectFirst("img")?.attr("src")
            val coverUrl = MangaMirUrls.upgradeCoverToMd(rawCover)

            val section = anchor.parents().firstOrNull { it.tagName() == "section" }
            val type = section?.selectFirst(".badge.badge-neutral")?.text()?.trim()
            val status = parseSectionStatus(section)

            cards.add(MmCard(slug, title, coverUrl, type, status))
        }

        val hasNextPage = doc.select("a").any { it.text().contains("Вперёд") }
        return MmCatalogPage(cards, hasNextPage)
    }

    fun parseDetails(doc: Document, slug: String): MmDetails {
        val jsonLd = parseFirstJsonLd(doc)
        val title = jsonLd?.name?.ifBlank { null }
            ?: doc.selectFirst("h1")?.text()?.trim().orEmpty()

        val rawCover = jsonLd?.imageUrl
            ?: doc.select("img[src*=/posters/]").firstOrNull { img ->
                img.parents().none { it.attr("x-data").startsWith("bookCarousel") }
            }?.attr("src")
        val coverUrl = MangaMirUrls.removeCoverSuffix(rawCover)

        val description = extractDescription(doc, jsonLd?.description)
        val ageRating = jsonLd?.keywords?.firstOrNull { it.matches(AGE_RATING_REGEX) }
        val tags = jsonLd?.keywords?.filter { it != ageRating } ?: emptyList()

        val genres = if (!jsonLd?.genres.isNullOrEmpty()) {
            jsonLd.genres
        } else {
            extractDomGenres(doc)
        }

        val type = doc.select("a[href*=\"type[0]=\"] .badge").firstOrNull()?.text()?.trim()
            ?: doc.selectFirst(".badge.badge-neutral")?.text()?.trim()
        val statusText = doc.select("a[href*=\"status[0]=\"] .badge").firstOrNull()?.text()
        val status = parseStatus(statusText)

        val related = parseRelatedCards(doc)

        return MmDetails(
            slug = slug,
            title = title,
            description = description,
            coverUrl = coverUrl,
            genres = genres,
            tags = tags,
            type = type,
            ageRating = ageRating,
            rating = jsonLd?.ratingValue,
            ratingCount = jsonLd?.ratingCount,
            status = status,
            related = related,
        )
    }

    fun parseChapters(doc: Document): List<MmChapter> {
        val jsonLd = parseFirstJsonLd(doc)
        val dateMap = extractDomDatesByUrl(doc)

        if (!jsonLd?.chapters.isNullOrEmpty()) {
            return parseJsonLdChapters(jsonLd.chapters, dateMap)
        }
        return parseDomFallbackChapters(doc)
    }

    fun parsePages(doc: Document): List<MmPage> {
        val reader = doc.selectFirst("[x-data*=\"reader\"]") ?: doc
        val images = reader.select("img[src*=\"/pages/\"]")
        val seenUrls = mutableSetOf<String>()
        val result = mutableListOf<MmPage>()

        val sorted = images.sortedBy { it.attr("data-number").toIntOrNull() ?: Int.MAX_VALUE }
        for (img in sorted) {
            val src = img.attr("src")
            if (src.isBlank() || !seenUrls.add(src)) continue

            val width = img.attr("width").toIntOrNull()
            val height = img.attr("height").toIntOrNull()
            result.add(MmPage(index = result.size, imageUrl = src, width = width, height = height))
        }
        return result
    }

    private fun parseFirstJsonLd(doc: Document): ParsedJsonLd? {
        val scripts = doc.select("script[type=\"application/ld+json\"]")
        for (script in scripts) {
            val parsed = MangaMirJsonLd.parse(script.data())
            if (parsed != null) return parsed
        }
        return null
    }

    private fun extractCardTitle(anchor: Element): String {
        val attrTitle = anchor.attr("title").trim()
        if (attrTitle.isNotEmpty()) return attrTitle
        val alt = anchor.selectFirst("img")?.attr("alt").orEmpty().trim()
        return alt.removeSuffix(" обложка манги").trim()
    }

    private fun parseSectionStatus(section: Element?): MmStatus {
        if (section == null) return MmStatus.UNKNOWN
        val badges = section.select(".badge")
        for (badge in badges) {
            val status = parseStatus(badge.text())
            if (status != MmStatus.UNKNOWN) return status
        }
        return MmStatus.UNKNOWN
    }

    private fun parseStatus(text: String?): MmStatus {
        if (text.isNullOrBlank()) return MmStatus.UNKNOWN
        val trimmed = text.trim()
        return when {
            trimmed.contains("Выпускается", ignoreCase = true) || trimmed.contains("Ongoing", ignoreCase = true) ->
                MmStatus.ONGOING
            trimmed.contains("Выпущено", ignoreCase = true) ||
                trimmed.contains("Завершён", ignoreCase = true) ||
                trimmed.contains("Завершен", ignoreCase = true) ||
                trimmed.contains("Finished", ignoreCase = true) ->
                MmStatus.COMPLETED
            else -> MmStatus.UNKNOWN
        }
    }

    private fun extractDescription(doc: Document, fallbackDesc: String?): String? {
        val div = doc.selectFirst("[x-data*=\"showMore\"] [x-ref=\"content\"] h2 + div")
        if (div != null) {
            val clone = div.clone()
            clone.select("br").forEach { it.replaceWith(TextNode("\n")) }
            val text = clone.wholeText().trim()
            if (text.isNotEmpty()) return text
        }
        return fallbackDesc?.ifBlank { null }
    }

    private fun extractDomGenres(doc: Document): List<String> {
        return doc.select("a[href^=\"/genre/\"]")
            .filter { a -> a.parents().none { it.attr("x-data").startsWith("bookCarousel") } }
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun parseRelatedCards(doc: Document): List<MmCard> {
        val carouselAnchors = doc.select("[x-data^=\"bookCarousel\"] a[data-card-link-type=\"poster\"]")
        val related = mutableListOf<MmCard>()
        val seen = mutableSetOf<String>()

        for (anchor in carouselAnchors) {
            val slug = MangaMirUrls.slugFromMangaUrl(anchor.attr("href"))
            if (slug.isBlank() || !seen.add(slug)) continue

            val title = extractCardTitle(anchor)
            val rawCover = anchor.selectFirst("img")?.attr("src")
            val coverUrl = MangaMirUrls.upgradeCoverToMd(rawCover)
            val section = anchor.parents().firstOrNull { it.tagName() == "section" }
            val type = section?.selectFirst(".badge.badge-neutral")?.text()?.trim()
            val status = parseSectionStatus(section)
            related.add(MmCard(slug, title, coverUrl, type, status))
        }
        return related
    }

    private fun extractDomDatesByUrl(doc: Document): Map<String, Long> {
        val rows = doc.select("li.list-row")
        return rows.mapNotNull { row ->
            val a = row.selectFirst("a[href]:not(.btn)") ?: row.selectFirst("a[href]") ?: return@mapNotNull null
            val time = row.selectFirst("time[datetime]")?.attr("datetime") ?: return@mapNotNull null
            val millis = parseIsoDate(time) ?: return@mapNotNull null
            val path = MangaMirUrls.normalizePath(a.attr("href"))
            path to millis
        }.toMap()
    }

    private fun parseJsonLdChapters(
        chapters: List<ParsedJsonLdChapter>,
        dateMap: Map<String, Long>,
    ): List<MmChapter> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<MmChapter>()

        val sorted = chapters.sortedByDescending { it.position }
        for (c in sorted) {
            val path = MangaMirUrls.normalizePath(c.url)
            if (!seen.add(path)) continue

            val volume = parseVolume(c.name)
            val numberText = parseChapterNumberText(c.name)
            val number = numberText?.toDoubleOrNull() ?: parseFallbackNumber(c.name)
            val date = dateMap[path]

            result.add(
                MmChapter(
                    url = path,
                    name = c.name,
                    volume = volume,
                    numberText = numberText,
                    number = number,
                    position = c.position,
                    uploadedAtMillis = date,
                ),
            )
        }
        return result
    }

    private fun parseDomFallbackChapters(doc: Document): List<MmChapter> {
        val rows = doc.select("li.list-row").filter { it.selectFirst("time[datetime]") != null }
        val seen = mutableSetOf<String>()
        val total = rows.size

        return rows.mapIndexedNotNull { index, row ->
            val a = row.selectFirst("a[href]:not(.btn)") ?: row.selectFirst("a[href]") ?: return@mapIndexedNotNull null
            val path = MangaMirUrls.normalizePath(a.attr("href"))
            if (!seen.add(path)) return@mapIndexedNotNull null

            val name = a.attr("title").ifBlank { a.text() }.trim()
            val time = row.selectFirst("time[datetime]")?.attr("datetime")
            val date = parseIsoDate(time)
            val volume = parseVolume(name)
            val numberText = parseChapterNumberText(name)
            val number = numberText?.toDoubleOrNull() ?: parseFallbackNumber(name)
            val position = total - index

            MmChapter(
                url = path,
                name = name,
                volume = volume,
                numberText = numberText,
                number = number,
                position = position,
                uploadedAtMillis = date,
            )
        }.sortedByDescending { it.position }
    }

    private fun parseVolume(name: String): Int? {
        return VOLUME_REGEX.find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseChapterNumberText(name: String): String? {
        val chapterMatch = CHAPTER_REGEX.find(name)?.groupValues?.get(1)
        if (chapterMatch != null) return chapterMatch.replace(',', '.')

        val withoutVolume = name.replace(VOLUME_REGEX, "")
        val numMatch = NUMBER_REGEX.find(withoutVolume)?.groupValues?.get(1)
        if (numMatch != null) return numMatch.replace(',', '.')

        val parsed = ChapterNumberParser.parse(name)
        if (parsed >= 0f) {
            return if (parsed % 1.0f == 0.0f) parsed.toInt().toString() else parsed.toString()
        }
        return null
    }

    private fun parseFallbackNumber(name: String): Double? {
        val parsed = ChapterNumberParser.parse(name)
        return if (parsed >= 0f) parsed.toDouble() else null
    }

    private fun parseIsoDate(datetime: String?): Long? {
        if (datetime.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(datetime).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
