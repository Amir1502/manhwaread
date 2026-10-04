package com.manhwaread.source.mangamir

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MangaMirSource(
    private val client: OkHttpClient,
    baseUrl: String = MangaMirUrls.DEFAULT_BASE_URL,
    private val rateLimiter: RateLimiter = RateLimiter(MAX_CONCURRENT, MIN_INTERVAL_MS),
    private val clock: () -> Long = System::currentTimeMillis,
) : Source {
    override val id: Long = MANGAMIR_SOURCE_ID
    override val name: String = SOURCE_NAME
    override val lang: String = "ru"
    override val baseUrl: String = baseUrl.trimEnd('/')
    override val supportsSearch: Boolean = true
    override val isNsfw: Boolean = false

    private val detailsCacheMutex = Mutex()
    private var cachedSlug: String? = null
    private var cachedDoc: Document? = null
    private var cachedTimestamp: Long = 0L

    override suspend fun getPopular(page: Int): MangasPage {
        val url = MangaMirUrls.buildCatalogUrl(baseUrl, page, MangaMirUrls.SORT_POPULAR, MangaMirUrls.DIR_DESC)
        val doc = getDocument(url)
        val catalog = MangaMirParser.parseCatalog(doc)
        return MangasPage(
            mangas = catalog.items.map { it.toSManga(id) },
            hasNextPage = catalog.hasNextPage,
        )
    }

    override suspend fun getLatest(page: Int): MangasPage {
        val url = MangaMirUrls.buildCatalogUrl(baseUrl, page, MangaMirUrls.SORT_LATEST, MangaMirUrls.DIR_DESC)
        val doc = getDocument(url)
        val catalog = MangaMirParser.parseCatalog(doc)
        return MangasPage(
            mangas = catalog.items.map { it.toSManga(id) },
            hasNextPage = catalog.hasNextPage,
        )
    }

    override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage {
        val url = MangaMirUrls.buildSearchUrl(baseUrl, query, page)
        val doc = getDocument(url)
        val catalog = MangaMirParser.parseCatalog(doc)
        return MangasPage(
            mangas = catalog.items.map { it.toSManga(id) },
            hasNextPage = catalog.hasNextPage,
        )
    }

    override suspend fun getDetails(manga: SManga): SManga {
        val slug = MangaMirUrls.slugFromMangaUrl(manga.url)
        val doc = loadDetailsDocument(slug, manga.url)
        val details = MangaMirParser.parseDetails(doc, slug)
        return details.toSManga(id, manga.url)
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val slug = MangaMirUrls.slugFromMangaUrl(manga.url)
        val doc = loadDetailsDocument(slug, manga.url)
        val chapters = MangaMirParser.parseChapters(doc)
        return chapters.map { it.toSChapter() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = MangaMirUrls.buildChapterUrl(baseUrl, chapter.url)
        val doc = getDocument(url)
        val pages = MangaMirParser.parsePages(doc)
        return pages.map { it.toPage() }
    }

    private suspend fun loadDetailsDocument(slug: String, mangaUrl: String): Document {
        detailsCacheMutex.withLock {
            val now = clock()
            if (cachedSlug == slug && (now - cachedTimestamp) < CACHE_TTL_MS && cachedDoc != null) {
                return cachedDoc!!
            }
            val url = MangaMirUrls.buildDetailsUrl(baseUrl, mangaUrl)
            val doc = getDocument(url)
            cachedSlug = slug
            cachedDoc = doc
            cachedTimestamp = now
            return doc
        }
    }

    private suspend fun getDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$baseUrl/")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

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

    private fun errorFor(code: Int, retryAfterHeader: String?): AppError {
        if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) {
            return AppError.ProviderAuth
        }
        if (code == HTTP_NOT_FOUND) {
            return AppError.SourceUnavailable
        }
        if (code == HTTP_TOO_MANY_REQUESTS) {
            val retryAfterMs = retryAfterHeader?.toLongOrNull()?.let { seconds -> seconds * MILLIS_PER_SECOND }
            return AppError.RateLimited(retryAfterMs)
        }
        return if (code in HTTP_SERVER_ERROR_RANGE) {
            AppError.Network(IOException("$SOURCE_NAME HTTP $code"))
        } else {
            AppError.ProviderBadResponse("$SOURCE_NAME HTTP $code")
        }
    }

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
        const val SOURCE_NAME = "MangaMir"
        private const val MAX_CONCURRENT = 3
        private const val MIN_INTERVAL_MS = 300L
        private const val RATE_LIMIT_DOMAIN = "mangamir.com"
        private const val CACHE_TTL_MS = 30_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val HEADER_RETRY_AFTER = "Retry-After"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MILLIS_PER_SECOND = 1_000L
        private val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
