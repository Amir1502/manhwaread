package com.manhwaread.sources.mangadex

import com.manhwaread.core.source.*
import com.manhwaread.sources.JsonTransport
import com.manhwaread.sources.SourceFormatException
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class MangaDexSource(private val transport: JsonTransport) : Source {
    override val id = ID
    override val name = "MangaDex"
    override val lang = "multi"
    override val baseUrl = "https://mangadex.org"
    override val supportsSearch = true
    override val isNsfw = false
    private val api = "https://api.mangadex.org".toHttpUrl()
    private val languages = setOf("ru", "en", "ko", "ja")

    override suspend fun getPopular(page: Int): MangasPage = catalog(null, page, "followedCount", languages)
    override suspend fun getLatest(page: Int): MangasPage = catalog(null, page, "updatedAt", languages)

    override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage {
        val selected = filters.filterIsInstance<Filter.Languages>().flatMap { it.values }.toSet()
        return catalog(query.trim().ifBlank { null }, page, if (query.isBlank()) "followedCount" else "relevance", selected.ifEmpty { languages })
    }

    private suspend fun catalog(query: String?, page: Int, order: String, selected: Set<String>): MangasPage {
        require(page in 1..(MAX_RESULTS / PAGE_SIZE))
        val offset = (page - 1) * PAGE_SIZE
        val url = endpoint("manga")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("order[$order]", "desc")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
        if (query != null) url.addQueryParameter("title", query)
        listOf("cover_art", "author", "artist").forEach { url.addQueryParameter("includes[]", it) }
        selected.sorted().forEach { url.addQueryParameter("availableTranslatedLanguage[]", it) }
        val result = request(url)
        val data = result.arrayAt("data")
        val total = total(result)
        return MangasPage(
            mangas = data.map { MangaDexParser.manga(it.asObject()) }.filterNot { it.nsfw },
            hasNextPage = data.isNotEmpty() && offset + data.size < minOf(total, MAX_RESULTS),
        )
    }

    override suspend fun getDetails(manga: SManga): SManga {
        require(manga.sourceId == id)
        val url = endpoint("manga", identifier(manga.url))
        listOf("cover_art", "author", "artist").forEach { url.addQueryParameter("includes[]", it) }
        val parsed = MangaDexParser.manga(request(url).objectAt("data"))
        if (parsed.nsfw) throw SourceFormatException("Adult content is disabled")
        return parsed
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        require(manga.sourceId == id && !manga.nsfw)
        val result = mutableListOf<SChapter>()
        var offset = 0
        while (true) {
            val url = endpoint("manga", identifier(manga.url), "feed")
                .addQueryParameter("limit", "500")
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("order[chapter]", "asc")
                .addQueryParameter("includes[]", "scanlation_group")
                .addQueryParameter("contentRating[]", "safe")
                .addQueryParameter("contentRating[]", "suggestive")
            languages.sorted().forEach { url.addQueryParameter("translatedLanguage[]", it) }
            val response = request(url)
            val data = response.arrayAt("data")
            val total = total(response)
            result += data.mapNotNull { MangaDexParser.chapter(it.asObject()) }
            offset += data.size
            if (offset >= total) break
            if (data.isEmpty()) throw SourceFormatException("Chapter pagination ended before the declared total")
            if (offset >= MAX_RESULTS) throw SourceFormatException("Chapter feed exceeds the API pagination limit")
        }
        return result.distinctBy { it.url }.sortedWith(ChapterOrder)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = request(endpoint("at-home", "server", identifier(chapter.url)))
        val host = response.requiredText("baseUrl").toHttpUrl()
        if (!host.isHttps) throw SourceFormatException("Image server must use HTTPS")
        val content = response.objectAt("chapter")
        val hash = content.requiredText("hash")
        val files = content.arrayAt("data")
        if (files.isEmpty()) throw SourceFormatException("Chapter has no hosted pages")
        return files.mapIndexed { index, element ->
            val file = (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: throw SourceFormatException("Missing page filename")
            Page(index, host.newBuilder().addPathSegment("data").addPathSegment(hash).addPathSegment(file).build().toString())
        }
    }

    override suspend fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${identifier(manga.url)}"
    override suspend fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${identifier(chapter.url)}"

    private fun endpoint(vararg parts: String): HttpUrl.Builder = api.newBuilder().apply {
        parts.forEach { addPathSegment(it) }
    }

    private suspend fun request(builder: HttpUrl.Builder): JsonObject {
        val result = transport.get(builder.build())
        if (result.text("result") != "ok") throw SourceFormatException("MangaDex reported an API error")
        return result
    }

    private fun total(response: JsonObject): Int = response.text("total")?.toIntOrNull()?.takeIf { it >= 0 }
        ?: throw SourceFormatException("Missing pagination total")

    private fun identifier(value: String): String {
        require(value.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")))
        return value
    }

    companion object {
        const val ID = 1L
        private const val PAGE_SIZE = 30
        private const val MAX_RESULTS = 10_000
    }
}
