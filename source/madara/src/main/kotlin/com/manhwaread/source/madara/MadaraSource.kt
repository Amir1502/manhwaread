package com.manhwaread.source.madara

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
import okhttp3.FormBody
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
 * Универсальный источник WordPress+Madara (manga18fx и сотни сайтов той же темы).
 * Контракт Source закреплён (AGENTS.md); url тайтлов/глав — пути от [baseUrl]
 * (формат MangaDex-источника).
 *
 * - каталог: GET {mangaPath}/page/{n}/?m_orderby=views|latest;
 * - поиск: GET /page/{n}/?s={query}&post_type=wp-manga;
 * - главы: li.wp-manga-chapter со страницы тайтла, фолбэк — POST admin-ajax
 *   (manga_get_chapters) при [MadaraConfig.useAjaxChapters];
 * - возрастной гейт: заголовок Cookie из [MadaraConfig.adultCookie];
 * - пустая выдача там, где её быть не должно → SourceLayoutChanged
 *   (сайт сменил разметку, источник не падает необработанно).
 */
class MadaraSource(
    private val config: MadaraConfig,
    private val client: OkHttpClient,
    private val rateLimiter: RateLimiter = RateLimiter(MAX_CONCURRENT, MIN_INTERVAL_MS),
    private val clock: () -> Long = System::currentTimeMillis,
) : Source {
    override val id: Long = config.id
    override val name: String = config.name
    override val lang: String = config.lang
    override val baseUrl: String = config.baseUrl
    override val supportsSearch: Boolean = true
    override val isNsfw: Boolean = config.isNsfw

    override suspend fun getPopular(page: Int): MangasPage =
        listPage("${config.baseUrl}${config.mangaPath}/page/$page/?m_orderby=${config.orderByPopular}")

    override suspend fun getLatest(page: Int): MangasPage =
        listPage("${config.baseUrl}${config.mangaPath}/page/$page/?m_orderby=${config.orderByLatest}")

    override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage {
        val trimmed = query.trim()
        // ФАЗА 13: текстовый поиск и Sort-фильтр; остальные фильтры подключит UI ФАЗЫ 14.
        val orderBy = sortOrderBy(filters) ?: config.orderByPopular
        if (trimmed.isEmpty()) {
            return listPage("${config.baseUrl}${config.mangaPath}/page/$page/?m_orderby=$orderBy")
        }
        val url = "${config.baseUrl}/page/$page/".toHttpUrl().newBuilder()
            .addQueryParameter("s", trimmed)
            .addQueryParameter("post_type", "wp-manga")
            .addQueryParameter("m_orderby", "relevance")
            .build()
        return listPage(url.toString())
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val document = getDocument("${config.baseUrl}${manga.url}")
        return MadaraHtml.parseDetails(document, manga.url, id, config.isNsfw)
            ?: throw layoutChanged("details page of ${manga.url}")
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val document = getDocument("${config.baseUrl}${manga.url}")
        var chapters = MadaraHtml.parseChapters(document, config.baseUrl, clock())
        if (chapters.isEmpty() && config.useAjaxChapters) {
            chapters = loadChaptersViaAjax(document)
        }
        if (chapters.isEmpty()) throw layoutChanged("chapter list of ${manga.url}")
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = getDocument("${config.baseUrl}${chapter.url}")
        val pages = MadaraHtml.parsePages(document)
        if (pages.isEmpty()) throw layoutChanged("pages of ${chapter.url}")
        return pages
    }

    private fun sortOrderBy(filters: List<Filter>): String? {
        val sort = filters.filterIsInstance<Filter.Sort>().firstOrNull() ?: return null
        return when (sort.selectedIndex) {
            SORT_INDEX_POPULAR -> config.orderByPopular
            SORT_INDEX_LATEST -> config.orderByLatest
            else -> null
        }
    }

    private suspend fun loadChaptersViaAjax(detailsDocument: Document): List<SChapter> {
        val holderId = MadaraHtml.chaptersHolderId(detailsDocument)
        if (holderId.isBlank()) return emptyList()
        val form = FormBody.Builder()
            .add("action", "manga_get_chapters")
            .add("manga", holderId)
            .build()
        val document = postDocument("${config.baseUrl}/wp-admin/admin-ajax.php", form)
        return MadaraHtml.parseChapters(document, config.baseUrl, clock())
    }

    private suspend fun listPage(url: String): MangasPage {
        val document = getDocument(url)
        val page = MadaraHtml.parseMangaList(document, config.baseUrl, id)
        // Пустой список без разметки карточек — сбой темы, а не «нет тайтлов».
        if (page.mangas.isEmpty()) throw layoutChanged("list page $url")
        return page
    }

    private suspend fun getDocument(url: String): Document = fetch(url) { Request.Builder().url(url) }

    private suspend fun postDocument(url: String, body: FormBody): Document =
        fetch(url) { Request.Builder().url(url).post(body) }

    private suspend fun fetch(url: String, build: () -> Request.Builder): Document {
        val builder = build()
        config.adultCookie?.let { cookie -> builder.addHeader(HEADER_COOKIE, cookie) }
        val request = builder.build()
        val response = rateLimiter.withPermit(config.baseUrl) {
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
        code in HTTP_SERVER_ERROR_RANGE -> AppError.Network(IOException("${config.name} HTTP $code"))
        else -> AppError.ProviderBadResponse("${config.name} HTTP $code")
    }

    private fun layoutChanged(what: String) = SourceException(
        AppError.SourceLayoutChanged,
        "${config.name}: layout changed for $what — update the Madara config",
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
        private const val MAX_CONCURRENT = 3
        private const val MIN_INTERVAL_MS = 300L
        private const val SORT_INDEX_POPULAR = 0
        private const val SORT_INDEX_LATEST = 1
        private const val HEADER_COOKIE = "Cookie"
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MILLIS_PER_SECOND = 1_000L
        private val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
