package com.manhwaread.source.mangadex

import com.manhwaread.core.common.AppError
import com.manhwaread.core.network.RateLimiter
import com.manhwaread.core.network.asAppError
import com.manhwaread.source.api.Filter
import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.Source
import com.manhwaread.source.api.SourceException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Источник MangaDex (API v5, https://api.mangadex.org). Контракт Source закреплён (AGENTS.md).
 *
 * - каталог: GET /manga?includes[]=cover_art&order[followedCount|latestUploadedChapter|relevance]=desc
 *   &limit=32&offset=(page-1)*32&contentRating[]=safe|suggestive|erotica[&title=query];
 * - главы: GET /manga/{id}/feed?translatedLanguage[]=ru&translatedLanguage[]=en&limit=200
 *   &order[chapter]=desc&includes[]=scanlation_group — пагинация до total, главы с externalUrl пропускаются;
 * - страницы: GET /at-home/server/{chapterId} → baseUrl + hash + имена файлов;
 * - ошибки: 401/403→ProviderAuth, 404→SourceUnavailable, 429→RateLimited(Retry-After),
 *   5xx→Network, битый JSON→ProviderBadResponse, Cloudflare→CloudflareBlocked.
 *
 * Все запросы проходят через RateLimiter (не чаще 5 req/s).
 */
class MangaDexSource(
    private val client: OkHttpClient,
    baseApiUrl: String = DEFAULT_BASE_URL,
    private val rateLimiter: RateLimiter = RateLimiter(MAX_CONCURRENT, MIN_INTERVAL_MS),
) : Source {
    override val id: Long = SOURCE_ID
    override val name: String = "MangaDex"
    override val lang: String = "multi"
    override val baseUrl: String = baseApiUrl
    override val supportsSearch: Boolean = true
    override val isNsfw: Boolean = false

    override suspend fun getPopular(page: Int): MangasPage =
        getMangaList(page, orderKey = "followedCount", title = null)

    override suspend fun getLatest(page: Int): MangasPage =
        getMangaList(page, orderKey = "latestUploadedChapter", title = null)

    override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage {
        // ФАЗА 4: только текстовый поиск по названию; пустой запрос вырождается в popular.
        val title = query.trim().ifBlank { null }
        val orderKey = if (title == null) "followedCount" else "relevance"
        return getMangaList(page, orderKey = orderKey, title = title)
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val mangaId = manga.url.substringAfterLast('/')
        val params = listOf(
            "includes[]" to "cover_art",
            "includes[]" to "author",
            "includes[]" to "artist",
        )
        val json = getJson("/manga/$mangaId", params)
        val data = json["data"] as? JsonObject
            ?: throw SourceException(AppError.ProviderBadResponse("details: missing data object"))
        return MangaDexJson.toSManga(data, SOURCE_ID).copy(initialized = true)
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.url.substringAfterLast('/')
        val chapters = mutableListOf<SChapter>()
        var offset = 0
        var total = Long.MAX_VALUE
        var pagesLoaded = 0
        while (pagesLoaded < MAX_FEED_PAGES && offset < total) {
            val json = getJson("/manga/$mangaId/feed", feedParams(offset))
            val data = MangaDexJson.dataArray(json)
            total = json["total"]?.let { MangaDexJson.longValue(it) } ?: 0L
            if (data.isEmpty()) {
                // Сервер расходится с total — защищаемся от бесконечного цикла.
                total = 0L
            }
            chapters += data.mapNotNull { element ->
                (element as? JsonObject)?.let { MangaDexJson.toSChapter(it) }
            }
            offset += data.size
            pagesLoaded++
        }
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('/')
        val json = getJson("/at-home/server/$chapterId", emptyList())
        return MangaDexJson.parseAtHomePages(json)
    }

    private suspend fun getMangaList(page: Int, orderKey: String, title: String?): MangasPage {
        val offset = (page - 1).coerceAtLeast(0) * MANGA_PAGE_SIZE
        val params = buildList {
            add("includes[]" to "cover_art")
            add("order[$orderKey]" to "desc")
            add("limit" to MANGA_PAGE_SIZE.toString())
            add("offset" to offset.toString())
            CONTENT_RATINGS.forEach { add("contentRating[]" to it) }
            if (title != null) add("title" to title)
        }
        val json = getJson("/manga", params)
        val data = MangaDexJson.dataArray(json)
        val mangas = data.mapNotNull { element ->
            (element as? JsonObject)?.let { MangaDexJson.toSManga(it, SOURCE_ID) }
        }
        val total = json["total"]?.let { MangaDexJson.longValue(it) } ?: 0L
        return MangasPage(mangas = mangas, hasNextPage = offset + data.size < total)
    }

    private fun feedParams(offset: Int): List<Pair<String, String>> = listOf(
        "translatedLanguage[]" to "ru",
        "translatedLanguage[]" to "en",
        "limit" to FEED_PAGE_SIZE.toString(),
        "offset" to offset.toString(),
        "order[chapter]" to "desc",
        "includes[]" to "scanlation_group",
        "contentRating[]" to "safe",
        "contentRating[]" to "suggestive",
        "contentRating[]" to "erotica",
    )

    private suspend fun getJson(path: String, params: List<Pair<String, String>>): JsonObject {
        val request = Request.Builder().url(buildUrl(path, params)).build()
        val response = rateLimiter.withPermit(RATE_LIMIT_DOMAIN) {
            try {
                client.newCall(request).awaitResponse()
            } catch (e: IOException) {
                throw SourceException(e.asAppError(), cause = e)
            }
        }
        return response.use { handleResponse(it) }
    }

    private fun buildUrl(path: String, params: List<Pair<String, String>>): HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        for ((key, value) in params) {
            builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    private fun handleResponse(response: Response): JsonObject {
        if (!response.isSuccessful) {
            throw SourceException(errorFor(response.code, response.header("Retry-After")))
        }
        val body = response.body?.string().orEmpty()
        val element = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            throw SourceException(AppError.ProviderBadResponse("invalid JSON: ${e.message}"), cause = e)
        }
        return element ?: throw SourceException(AppError.ProviderBadResponse("root element is not a JSON object"))
    }

    private fun errorFor(code: Int, retryAfterHeader: String?): AppError = when {
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> AppError.ProviderAuth
        code == HTTP_NOT_FOUND -> AppError.SourceUnavailable
        code == HTTP_TOO_MANY_REQUESTS ->
            AppError.RateLimited(retryAfterHeader?.toLongOrNull()?.let { it * MILLIS_PER_SECOND })
        code in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> AppError.Network(IOException("MangaDex HTTP $code"))
        else -> AppError.ProviderBadResponse("MangaDex HTTP $code")
    }

    // Интерсептор OkHttp синхронный: мост в suspend через enqueue без блокировки потока.
    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        const val SOURCE_ID = 1L
        const val DEFAULT_BASE_URL = "https://api.mangadex.org"
        private const val MAX_CONCURRENT = 5
        private const val MIN_INTERVAL_MS = 200L
        private const val MANGA_PAGE_SIZE = 32
        private const val FEED_PAGE_SIZE = 200
        private const val MAX_FEED_PAGES = 10
        private const val RATE_LIMIT_DOMAIN = "mangadex.org"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR_MIN = 500
        private const val HTTP_SERVER_ERROR_MAX = 599
        private const val MILLIS_PER_SECOND = 1000L
        private val CONTENT_RATINGS = listOf("safe", "suggestive", "erotica")
    }
}
