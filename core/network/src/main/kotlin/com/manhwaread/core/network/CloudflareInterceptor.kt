package com.manhwaread.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Исключение: challenge Cloudflare не пройден.
 * Вызывающий код маппит его в [com.manhwaread.core.common.AppError.CloudflareBlocked]
 * через [asAppError].
 */
class CloudflareBlockedException(val url: String) : IOException("Cloudflare challenge not solved: $url")

/** Результат прохождения challenge: cookie cf_clearance и UA, с которым он получен. */
data class CfClearance(val value: String, val userAgent: String)

/**
 * Решатель challenge-страниц Cloudflare. В ФАЗЕ 3 реализация только noop;
 * WebView-решатель подключается в ФАЗЕ 13 (Android-слой).
 */
interface CloudflareChallengeSolver {
    /** Возвращает clearance для [url] или null, если challenge не решаем. */
    suspend fun solve(url: String): CfClearance?
}

/** Решатель по умолчанию: ничего не делает — защищённый источник даёт CloudflareBlocked. */
object NoopChallengeSolver : CloudflareChallengeSolver {
    override suspend fun solve(url: String): CfClearance? = null
}

/** Хранилище cookie cf_clearance по доменам. */
interface CfCookieStore {
    fun get(domain: String): CfClearance?
    fun put(domain: String, clearance: CfClearance)
    fun remove(domain: String)
}

/** Хранилище в памяти процесса (на сессию приложения). */
class InMemoryCfCookieStore : CfCookieStore {
    private val map = ConcurrentHashMap<String, CfClearance>()
    override fun get(domain: String): CfClearance? = map[domain]
    override fun put(domain: String, clearance: CfClearance) {
        map[domain] = clearance
    }

    override fun remove(domain: String) {
        map.remove(domain)
    }
}

/**
 * Перехват challenge-страниц Cloudflare:
 * - прикрепляет сохранённый cf_clearance к запросам того же домена;
 * - на 403/503 с `Server: cloudflare` и маркерами «Just a moment»/«cf-chl»
 *   вызывает [solver] и повторяет запрос с полученным cookie;
 * - solver вернул null → [CloudflareBlockedException] (→ CloudflareBlocked).
 *
 * Интерсептор OkHttp синхронный, поэтому suspend-solver вызывается через
 * [runBlocking] на потоке OkHttp — это допустимо и не блокирует main thread.
 */
class CloudflareInterceptor(
    private val solver: CloudflareChallengeSolver,
    private val cookieStore: CfCookieStore = InMemoryCfCookieStore(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val domain = request.url.host
        val stored = cookieStore.get(domain)

        val builder = request.newBuilder()
        if (stored != null) {
            builder.header(COOKIE_HEADER, "cf_clearance=${stored.value}")
            builder.header("User-Agent", stored.userAgent)
        }
        val response = chain.proceed(builder.build())
        if (!response.isCloudflareChallenge()) return response

        response.close()
        val clearance = runBlocking { solver.solve(request.url.toString()) }
            ?: throw CloudflareBlockedException(request.url.toString())
        cookieStore.put(domain, clearance)

        val retry = request.newBuilder()
            .header(COOKIE_HEADER, "cf_clearance=${clearance.value}")
            .header("User-Agent", clearance.userAgent)
            .build()
        return chain.proceed(retry)
    }

    // 403/503 + Server: cloudflare + маркеры challenge-страницы в теле.
    private fun Response.isCloudflareChallenge(): Boolean {
        val statusMatch = code == STATUS_FORBIDDEN || code == STATUS_UNAVAILABLE
        val serverMatch = header("Server")?.equals("cloudflare", ignoreCase = true) == true
        if (!statusMatch || !serverMatch) return false
        val body = peekBody(CHALLENGE_PEEK_BYTES).string().lowercase()
        return "just a moment" in body || "cf-chl" in body
    }

    private companion object {
        const val COOKIE_HEADER = "Cookie"
        const val STATUS_FORBIDDEN = 403
        const val STATUS_UNAVAILABLE = 503
        const val CHALLENGE_PEEK_BYTES = 1L shl 20 // 1 MiB достаточно для поиска маркеров
    }
}
