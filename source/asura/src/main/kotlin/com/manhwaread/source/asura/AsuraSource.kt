package com.manhwaread.source.asura

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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Источник Asura Scans (asuracomic.net, Next.js-вёрстка). Контракт Source
 * закреплён (AGENTS.md); url тайтлов/глав — пути от baseUrl (формат MangaDex).
 *
 * Селекторы и пути вынесены в [AsuraManifest]: при смене вёрстки сайт перестаёт
 * парситься, источник сообщает SourceLayoutChanged «update the manifest» и не
 * падает (DoD ФАЗЫ 13).
 *
 * - каталог: GET /comics?page={n}&sort=popular|latest[&name={query}];
 * - карточка: GET /comic/{slug} → h1/og:meta/жанры/главы a[href*=/chapter-];
 * - страницы главы: JSON из script#__NEXT_DATA__ → массив ссылок на картинки.
 */
class AsuraSource(
    private val client: OkHttpClient,
    private val manifest: AsuraManifest = DEFAULT_ASURA_MANIFEST,
    private val rateLimiter: RateLimiter = RateLimiter(MAX_CONCURRENT, MIN_INTERVAL_MS),
    private val clock: () -> Long = System::currentTimeMillis,
) : Source {
    override val id: Long = SOURCE_ID
    override val name: String = SOURCE_NAME
    override val lang: String = "en"
    override val baseUrl: String = manifest.baseUrl
    override val supportsSearch: Boolean = true
    override val isNsfw: Boolean = false

    override suspend fun getPopular(page: Int): MangasPage =
        listPage(buildListUrl(page, manifest.popularSortValue, query = null))

    override suspend fun getLatest(page: Int): MangasPage =
        listPage(buildListUrl(page, manifest.latestSortValue, query = null))

    override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage {
        val trimmed = query.trim()
        // ФАЗА 13: текстовый поиск и Sort-фильтр; остальные фильтры подключит UI ФАЗЫ 14.
        val sort = sortValue(filters) ?: manifest.popularSortValue
        return listPage(buildListUrl(page, sort, trimmed.ifBlank { null }))
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val document = getDocument("${manifest.baseUrl}${manga.url}")
        return AsuraHtml.parseDetails(document, manga.url, SOURCE_ID)
            ?: throw layoutChanged("details page of ${manga.url}")
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val document = getDocument("${manifest.baseUrl}${manga.url}")
        val chapters = AsuraHtml.parseChapters(document, manifest, clock())
        if (chapters.isEmpty()) throw layoutChanged("chapter list of ${manga.url}")
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = getDocument("${manifest.baseUrl}${chapter.url}")
        val pages = AsuraHtml.parsePages(document, manifest)
        if (pages.isEmpty()) throw layoutChanged("pages of ${chapter.url}")
        return pages
    }

    private fun buildListUrl(page: Int, sort: String, query: String?): String {
        val builder = "${manifest.baseUrl}${manifest.comicListPath}".toHttpUrl().newBuilder()
            .addQueryParameter(manifest.pageQueryParam, page.toString())
            .addQueryParameter(manifest.sortQueryParam, sort)
        if (query != null) {
            builder.addQueryParameter(manifest.searchQueryParam, query)
        }
        return builder.build().toString()
    }

    private fun sortValue(filters: List<Filter>): String? {
        val sort = filters.filterIsInstance<Filter.Sort>().firstOrNull() ?: return null
        return when (sort.selectedIndex) {
            SORT_INDEX_POPULAR -> manifest.popularSortValue
            SORT_INDEX_LATEST -> manifest.latestSortValue
            else -> null
        }
    }

    private suspend fun listPage(url: String): MangasPage {
        val document = getDocument(url)
        val page = AsuraHtml.parseMangaList(document, manifest, SOURCE_ID)
        if (page.mangas.isEmpty()) throw layoutChanged("list page $url")
        return page
    }

    private suspend fun getDocument(url: String): Document {
        val request = Request.Builder().url(url).build()
        val response = rateLimiter.withPermit(RATE_LIMIT_DOMAIN) {
            try {
                client.newCall(request).awaitResponse()
            } catch (io: IOException) {
                throw SourceException(io.asAppError(), cause = io)
            }
        }
        return response.use { parseResponse(it, url) }
    }

    private fun parseResponse(response: Response, url: String): Document {
        if (!response.isSuccessful) {
            throw SourceException(errorFor(response.code, response.header(HEADER_RETRY_AFTER)))
        }
        val body = response.body?.string().orEmpty()
        return Jsoup.parse(body, url)
    }

    private fun errorFor(code: Int, retryAfterHeader: String?): AppError = when {
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> AppError.ProviderAuth
        code == HTTP_NOT_FOUND -> AppError.SourceUnavailable
        code == HTTP_TOO_MANY_REQUESTS ->
            AppError.RateLimited(retryAfterHeader?.toLongOrNull()?.let { seconds -> seconds * MILLIS_PER_SECOND })
        code in HTTP_SERVER_ERROR_RANGE -> AppError.Network(IOException("$SOURCE_NAME HTTP $code"))
        else -> AppError.ProviderBadResponse("$SOURCE_NAME HTTP $code")
    }

    private fun layoutChanged(what: String) = SourceException(
        AppError.SourceLayoutChanged,
        "$SOURCE_NAME: layout changed for $what — update the manifest",
    )

    // Интерсептор OkHttp синхронный: мост в suspend через enqueue без блокировки потока.
    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }

    companion object {
        const val SOURCE_ID = 3L
        const val SOURCE_NAME = "Asura Scans"
        private const val MAX_CONCURRENT = 3
        private const val MIN_INTERVAL_MS = 300L
        private const val RATE_LIMIT_DOMAIN = "asuracomic.net"
        private const val SORT_INDEX_POPULAR = 0
        private const val SORT_INDEX_LATEST = 1
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MILLIS_PER_SECOND = 1_000L
        private val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
