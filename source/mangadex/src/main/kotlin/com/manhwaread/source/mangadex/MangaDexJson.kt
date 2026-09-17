package com.manhwaread.source.mangadex

import com.manhwaread.core.common.AppError
import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.SourceException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Разбор ответов MangaDex API v5 без DTO: навигация по JsonObject/JsonArray.
 * Любое отклонение формы ответа — [SourceException] c ProviderBadResponse.
 */
internal object MangaDexJson {
    private const val COVER_CDN_BASE = "https://uploads.mangadex.org/covers"
    private const val UNKNOWN_CHAPTER_NUMBER = -1f
    private val TITLE_LANG_PRIORITY = listOf("en", "ja", "ja-ro", "ru")
    private val NSFW_RATINGS = setOf("erotica", "pornographic")

    /** Массив `data` из ответа-коллекции. */
    fun dataArray(json: JsonObject): JsonArray = json["data"] as? JsonArray
        ?: throw SourceException(AppError.ProviderBadResponse("missing data array"))

    /** Числовое значение JsonPrimitive (total и др.). */
    fun longValue(element: JsonElement): Long? = (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    /** Элемент каталога/карточки → SManga. */
    fun toSManga(manga: JsonObject, sourceId: Long): SManga {
        val mangaId = manga.primitive("id")
            ?: throw SourceException(AppError.ProviderBadResponse("manga without id"))
        val attributes = manga["attributes"] as? JsonObject
            ?: throw SourceException(AppError.ProviderBadResponse("manga $mangaId without attributes"))
        val relationships = manga["relationships"] as? JsonArray ?: JsonArray(emptyList())
        val rating = attributes.primitive("contentRating")
        return SManga(
            url = "/manga/$mangaId",
            title = pickTitle(attributes, mangaId),
            sourceId = sourceId,
            artist = relationshipName(relationships, "artist"),
            author = relationshipName(relationships, "author"),
            description = pickLocalized(attributes["description"] as? JsonObject, listOf("ru", "en")),
            genres = extractGenres(attributes["tags"] as? JsonArray),
            status = mapStatus(attributes.primitive("status")),
            thumbnailUrl = coverUrl(mangaId, relationships),
            nsfw = rating != null && rating in NSFW_RATINGS,
        )
    }

    /** Элемент фида глав → SChapter; null для внешних глав (externalUrl != null). */
    fun toSChapter(element: JsonObject): SChapter? {
        val attributes = element["attributes"] as? JsonObject
            ?: throw SourceException(AppError.ProviderBadResponse("chapter without attributes"))
        if (attributes.primitive("externalUrl") != null) return null
        val chapterId = element.primitive("id")
            ?: throw SourceException(AppError.ProviderBadResponse("chapter without id"))
        val numberRaw = attributes.primitive("chapter")
        return SChapter(
            url = "/chapter/$chapterId",
            name = buildChapterName(numberRaw, attributes.primitive("title")),
            dateUpload = parseInstant(attributes.primitive("publishAt")),
            chapterNumber = numberRaw?.toFloatOrNull() ?: UNKNOWN_CHAPTER_NUMBER,
            scanlator = firstGroupName(element["relationships"] as? JsonArray),
        )
    }

    /** Ответ /at-home/server/{id} → список страниц с полными URL изображений. */
    fun parseAtHomePages(json: JsonObject): List<Page> {
        val base = json.primitive("baseUrl")
        val chapterData = json["chapter"] as? JsonObject
        val hash = chapterData?.primitive("hash")
        val files = chapterData?.get("data") as? JsonArray
        if (base.isNullOrBlank() || hash.isNullOrBlank() || files == null) {
            throw SourceException(AppError.ProviderBadResponse("at-home: unexpected response shape"))
        }
        return files.mapIndexedNotNull { index, element ->
            val fileName = (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            fileName?.let { Page(index = index, imageUrl = "$base/data/$hash/$it") }
        }
    }

    /** Статус MangaDex → MangaStatus; неизвестные значения → UNKNOWN. */
    fun mapStatus(raw: String?): MangaStatus = when (raw) {
        "ongoing" -> MangaStatus.ONGOING
        "completed" -> MangaStatus.COMPLETED
        "hiatus" -> MangaStatus.HIATUS
        "cancelled" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    /** ISO-8601 (2023-01-15T12:00:00+00:00) → epoch millis; сбой формата → 0. */
    fun parseInstant(raw: String?): Long {
        if (raw == null) return 0L
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }
    }

    // JsonNull.content даёт строку "null" — только contentOrNull.
    private fun JsonObject.primitive(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun pickTitle(attributes: JsonObject, mangaId: String): String {
        val titles = attributes["title"] as? JsonObject
            ?: throw SourceException(AppError.ProviderBadResponse("manga $mangaId without title object"))
        return pickLocalized(titles, TITLE_LANG_PRIORITY)
            ?: throw SourceException(AppError.ProviderBadResponse("manga $mangaId with empty title"))
    }

    // Первое непустое значение: сначала приоритетные языки, затем любой доступный.
    private fun pickLocalized(values: JsonObject?, priority: List<String>): String? {
        if (values == null) return null
        val ordered = priority.mapNotNull { lang -> (values[lang] as? JsonPrimitive)?.contentOrNull }
        val firstNonBlank = (ordered + values.values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
            .firstOrNull { it.isNotBlank() }
        return firstNonBlank
    }

    private fun extractGenres(tags: JsonArray?): List<String> {
        if (tags == null) return emptyList()
        return tags.mapNotNull { tag ->
            val attributes = (tag as? JsonObject)?.get("attributes") as? JsonObject
            pickLocalized(attributes?.get("name") as? JsonObject, listOf("en"))
        }
    }

    private fun coverUrl(mangaId: String, relationships: JsonArray): String? {
        val cover = relationships
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.primitive("type") == "cover_art" }
        val fileName = (cover?.get("attributes") as? JsonObject)?.primitive("fileName")
        return fileName?.let { "$COVER_CDN_BASE/$mangaId/$it.256.jpg" }
    }

    private fun relationshipName(relationships: JsonArray, type: String): String? {
        val match = relationships
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.primitive("type") == type }
        val name = (match?.get("attributes") as? JsonObject)?.primitive("name")
        return name?.takeIf { it.isNotBlank() }
    }

    private fun firstGroupName(relationships: JsonArray?): String? {
        val group = relationships
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.primitive("type") == "scanlation_group" }
        val name = (group?.get("attributes") as? JsonObject)?.primitive("name")
        return name?.takeIf { it.isNotBlank() }
    }

    private fun buildChapterName(numberRaw: String?, title: String?): String = when {
        !numberRaw.isNullOrBlank() && !title.isNullOrBlank() -> "Ch. $numberRaw - $title"
        !numberRaw.isNullOrBlank() -> "Ch. $numberRaw"
        !title.isNullOrBlank() -> title
        else -> "Chapter"
    }
}
