package com.manhwaread.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Добавляет User-Agent и Accept-Language, если их не задал источник,
 * а Referer/Origin — ТОЛЬКО для доменов из [refererByDomain]:
 * приватные заголовки источника не должны утекать на чужие CDN.
 */
class HeadersInterceptor(
    private val userAgent: String,
    private val acceptLanguage: String = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
    private val refererByDomain: Map<String, String> = emptyMap(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("User-Agent") == null) {
            builder.header("User-Agent", userAgent)
        }
        if (original.header("Accept-Language") == null) {
            builder.header("Accept-Language", acceptLanguage)
        }
        val referer = refererFor(original.url.host)
        if (referer != null && original.header("Referer") == null) {
            builder.header("Referer", referer)
            if (original.header("Origin") == null) {
                builder.header("Origin", originOf(referer))
            }
        }
        return chain.proceed(builder.build())
    }

    /**
     * Referer для хоста: точное совпадение с доменом или поддомен
     * (`sub.example.com` для `example.com`). `example.com.evil.org` НЕ совпадает.
     * Internal — для прямых unit-тестов без сервера.
     */
    internal fun refererFor(host: String): String? {
        for ((domain, referer) in refererByDomain) {
            if (host == domain || host.endsWith(".$domain")) return referer
        }
        return null
    }

    private fun originOf(referer: String): String {
        val parsed = referer.toHttpUrlOrNull() ?: return referer.trimEnd('/')
        val defaultPort = if (parsed.scheme == "https") HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
        val portSuffix = if (parsed.port != defaultPort) ":${parsed.port}" else ""
        return "${parsed.scheme}://${parsed.host}$portSuffix"
    }

    private companion object {
        const val HTTP_DEFAULT_PORT = 80
        const val HTTPS_DEFAULT_PORT = 443
    }
}
