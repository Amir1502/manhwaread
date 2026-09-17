package com.manhwaread.sources.mangadex

import com.manhwaread.core.source.MangaStatus
import com.manhwaread.core.source.SChapter
import com.manhwaread.core.source.SManga
import com.manhwaread.sources.SourceFormatException
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.objectAt(key: String): JsonObject = get(key) as? JsonObject
    ?: throw SourceFormatException("Missing object: $key")
internal fun JsonObject.arrayAt(key: String): JsonArray = get(key) as? JsonArray
    ?: throw SourceFormatException("Missing array: $key")
internal fun JsonObject.requiredText(key: String): String = text(key)?.takeIf { it.isNotBlank() }
    ?: throw SourceFormatException("Missing text: $key")
internal fun JsonElement.asObject(): JsonObject = this as? JsonObject
    ?: throw SourceFormatException("Expected an object")

internal object MangaDexParser {
    private val languageOrder = listOf("ru", "en", "ja", "ko", "zh")

    private fun localized(values: JsonObject): String? =
        languageOrder.firstNotNullOfOrNull { values.text(it)?.takeIf(String::isNotBlank) }
            ?: values.keys.sorted().firstNotNullOfOrNull { values.text(it)?.takeIf(String::isNotBlank) }

    fun manga(data: JsonObject): SManga {
        val id = data.requiredText("id")
        val attributes = data.objectAt("attributes")
        val relationships = data.arrayAt("relationships").map { it.asObject() }
        fun names(type: String): String? = relationships.filter { it.text("type") == type }
            .mapNotNull { (it["attributes"] as? JsonObject)?.text("name") }
            .distinct().joinToString(", ").ifBlank { null }
        val cover = relationships.firstOrNull { it.text("type") == "cover_art" }
            ?.get("attributes") as? JsonObject
        val fileName = cover?.text("fileName")
        val thumbnail = fileName?.let {
            "https://uploads.mangadex.org".toHttpUrl().newBuilder()
                .addPathSegment("covers").addPathSegment(id).addPathSegment("$it.256.jpg").build().toString()
        }
        return SManga(
            url = id,
            title = localized(attributes.objectAt("title")) ?: throw SourceFormatException("Missing manga title"),
            artist = names("artist"),
            author = names("author"),
            description = (attributes["description"] as? JsonObject)?.let(::localized),
            genres = attributes.arrayAt("tags").mapNotNull {
                localized(it.asObject().objectAt("attributes").objectAt("name"))
            }.distinct(),
            status = when (attributes.text("status")) {
                "ongoing" -> MangaStatus.ONGOING
                "completed" -> MangaStatus.COMPLETED
                "hiatus" -> MangaStatus.HIATUS
                "cancelled" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            },
            thumbnailUrl = thumbnail,
            sourceId = MangaDexSource.ID,
            nsfw = attributes.text("contentRating") !in setOf("safe", "suggestive"),
            initialized = true,
        )
    }

    fun chapter(data: JsonObject): SChapter? {
        val attributes = data.objectAt("attributes")
        // Внешние главы не имеют страниц на MangaDex и не выдаются как доступные для чтения.
        if (!attributes.text("externalUrl").isNullOrBlank()) return null
        val number = attributes.text("chapter")
        val title = attributes.text("title")
        val volume = attributes.text("volume")
        val name = listOfNotNull(
            volume?.let { "Vol. $it" },
            number?.let { "Chapter $it" },
            title?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { "Oneshot" }
        val groups = data.arrayAt("relationships").map { it.asObject() }
            .filter { it.text("type") == "scanlation_group" }
            .mapNotNull { (it["attributes"] as? JsonObject)?.text("name") }
        return SChapter(
            url = data.requiredText("id"),
            name = name,
            dateUpload = attributes.text("publishAt")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
            chapterNumber = number?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: -1f,
            scanlator = groups.distinct().joinToString(", ").ifBlank { null },
        )
    }
}
