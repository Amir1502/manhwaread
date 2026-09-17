package com.manhwaread.core.network

import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

/** User-Agent приложения по умолчанию (источник может переопределить на запрос). */
const val DEFAULT_USER_AGENT = "Manhwaread/0.1 (+https://gitlab.com/folzi-group/manhwaread)"

/**
 * Конфигурация HTTP-клиента: заголовки, таймауты, дисковый кэш,
 * решатель Cloudflare. Домены источников задают Referer через [refererByDomain].
 */
data class HttpClientConfig(
    val userAgent: String = DEFAULT_USER_AGENT,
    val acceptLanguage: String = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
    val refererByDomain: Map<String, String> = emptyMap(),
    val connectTimeoutMs: Long = 15_000L,
    val readTimeoutMs: Long = 30_000L,
    val cacheDirectory: File? = null,
    val cacheMaxBytes: Long = DEFAULT_CACHE_BYTES,
    val cloudflareSolver: CloudflareChallengeSolver = NoopChallengeSolver,
) {
    companion object {
        const val DEFAULT_CACHE_BYTES = 50L * 1024L * 1024L // 50 MiB
    }
}

/**
 * Сборка OkHttpClient с фиксированным порядком интерсепторов:
 * заголовки → Cloudflare → ETag-кэш. Дисковый кэш OkHttp включается,
 * если задан [HttpClientConfig.cacheDirectory].
 */
object HttpClientFactory {
    fun create(config: HttpClientConfig = HttpClientConfig()): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .addInterceptor(HeadersInterceptor(config.userAgent, config.acceptLanguage, config.refererByDomain))
            .addInterceptor(CloudflareInterceptor(config.cloudflareSolver))
            .addInterceptor(EtagCacheInterceptor())
        config.cacheDirectory?.let { dir -> builder.cache(Cache(dir, config.cacheMaxBytes)) }
        return builder.build()
    }

    private const val MAX_IDLE_CONNECTIONS = 8
    private const val KEEP_ALIVE_MINUTES = 5L
}
