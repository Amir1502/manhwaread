package com.manhwaread.source.mangamir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class ParsedJsonLd(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val genres: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val ratingValue: Double? = null,
    val ratingCount: Int? = null,
    val chapters: List<ParsedJsonLdChapter> = emptyList(),
)

internal data class ParsedJsonLdChapter(
    val name: String,
    val position: Int,
    val url: String,
)

internal object MangaMirJsonLd {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawJson: String): ParsedJsonLd? {
        return runCatching {
            val element = json.parseToJsonElement(rawJson)
            val objects = extractAllObjects(element)
            var comicSeries: JsonObject? = null
            var aggregateRating: JsonObject? = null

            for (obj in objects) {
                val types = extractTypes(obj)
                if (types.contains("ComicSeries")) {
                    comicSeries = obj
                }
                if (types.contains("AggregateRating")) {
                    aggregateRating = obj
                }
            }

            if (comicSeries == null && aggregateRating == null) return null

            buildParsedJsonLd(comicSeries, aggregateRating)
        }.getOrNull()
    }

    private fun buildParsedJsonLd(comicSeries: JsonObject?, aggregateRating: JsonObject?): ParsedJsonLd {
        val name = comicSeries?.get("name")?.jsonPrimitive?.contentOrNull
        val description = comicSeries?.get("description")?.jsonPrimitive?.contentOrNull
        val imageUrl = extractImageUrl(comicSeries?.get("image"))
        val genres = comicSeries?.get("genre")?.let { extractStringList(it) } ?: emptyList()
        val keywords = comicSeries?.get("keywords")?.let { extractStringList(it) } ?: emptyList()

        val ratingValue = aggregateRating?.get("ratingValue")?.jsonPrimitive?.doubleOrNull
        val ratingCount = aggregateRating?.get("ratingCount")?.jsonPrimitive?.intOrNull
        val chapters = extractChapters(comicSeries?.get("hasPart"))

        return ParsedJsonLd(
            name = name,
            description = description,
            imageUrl = imageUrl,
            genres = genres,
            keywords = keywords,
            ratingValue = ratingValue,
            ratingCount = ratingCount,
            chapters = chapters,
        )
    }

    private fun extractChapters(hasPart: JsonElement?): List<ParsedJsonLdChapter> {
        val array = hasPart as? JsonArray ?: return emptyList()
        return array.mapNotNull { part ->
            val obj = part as? JsonObject ?: return@mapNotNull null
            val partType = extractTypes(obj)
            if (partType.isNotEmpty() && !partType.contains("Chapter")) return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val pos = obj["position"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (name.isBlank() || url.isBlank()) return@mapNotNull null
            ParsedJsonLdChapter(name, pos, url)
        }
    }

    private fun extractAllObjects(element: JsonElement): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        when (element) {
            is JsonObject -> {
                result.add(element)
                val graph = element["@graph"] as? JsonArray
                graph?.filterIsInstance<JsonObject>()?.let { result.addAll(it) }
            }
            is JsonArray -> {
                element.filterIsInstance<JsonObject>().forEach { item ->
                    result.addAll(extractAllObjects(item))
                }
            }
            else -> Unit
        }
        return result
    }

    private fun extractTypes(obj: JsonObject): Set<String> {
        val typeElement = obj["@type"] ?: return emptySet()
        return when (typeElement) {
            is JsonArray -> typeElement.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
            else -> typeElement.jsonPrimitive.contentOrNull?.let { setOf(it) } ?: emptySet()
        }
    }

    private fun extractImageUrl(element: JsonElement?): String? {
        if (element == null) return null
        return when (element) {
            is JsonObject -> element["url"]?.jsonPrimitive?.contentOrNull
            is JsonArray -> element.firstOrNull()?.let { extractImageUrl(it) }
            else -> element.jsonPrimitive.contentOrNull
        }
    }

    private fun extractStringList(element: JsonElement): List<String> {
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }.filter { it.isNotEmpty() }
            else -> element.jsonPrimitive.contentOrNull?.let { listOf(it.trim()) } ?: emptyList()
        }
    }
}
