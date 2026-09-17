package com.manhwaread.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Тело ответа, сохранённое вместе с его ETag. */
class CachedBody(val etag: String, val bytes: ByteArray, val contentType: String?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedBody) return false
        return etag == other.etag && contentType == other.contentType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = etag.hashCode()
        result = HASH_PRIME * result + bytes.contentHashCode()
        result = HASH_PRIME * result + (contentType?.hashCode() ?: 0)
        return result
    }

    private companion object {
        const val HASH_PRIME = 31
    }
}

/** Хранилище {url → (etag, body)} для условных GET-запросов. */
interface EtagCacheStore {
    fun get(url: String): CachedBody?
    fun put(url: String, entry: CachedBody)
}

/** LRU-хранилище в памяти на [maxEntries] записей. */
class InMemoryEtagCacheStore(private val maxEntries: Int = 128) : EtagCacheStore {
    private val map = object : LinkedHashMap<String, CachedBody>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBody>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    override fun get(url: String): CachedBody? = map[url]

    @Synchronized
    override fun put(url: String, entry: CachedBody) {
        map[url] = entry
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * Условные запросы с ETag:
 * - есть кэш → добавляет If-None-Match;
 * - сервер ответил 304 → отдаёт сохранённое тело как 200 с заголовком
 *   `X-Etag-Cache: hit` (тело не скачивается повторно);
 * - успешный ответ с ETag → сохраняет тело (не более [MAX_CACHE_BYTES] на запись).
 *
 * Дополняет дисковый OkHttp-кэш: работает даже там, где источник не шлёт
 * Cache-Control (типично для JSON-API каталогов).
 */
class EtagCacheInterceptor(
    private val store: EtagCacheStore = InMemoryEtagCacheStore(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.toString()
        val cached = store.get(key)
        val conditional = if (cached != null) {
            request.newBuilder().header("If-None-Match", cached.etag).build()
        } else {
            request
        }
        val response = chain.proceed(conditional)

        if (response.code == STATUS_NOT_MODIFIED && cached != null) {
            val protocol = response.protocol
            response.close()
            return Response.Builder()
                .request(request)
                .protocol(protocol)
                .code(STATUS_OK)
                .message("OK (etag cache)")
                .header("X-Etag-Cache", "hit")
                .body(cached.bytes.toResponseBody(cached.contentType?.toMediaTypeOrNull()))
                .build()
        }

        if (response.isSuccessful) {
            val etag = response.header("ETag")
            if (etag != null) {
                // peekBody не потребляет основное тело — ответ остаётся пригодным для чтения.
                val peek = response.peekBody(MAX_CACHE_BYTES)
                store.put(key, CachedBody(etag, peek.bytes(), response.header("Content-Type")))
            }
        }
        return response
    }

    private companion object {
        const val STATUS_OK = 200
        const val STATUS_NOT_MODIFIED = 304
        const val MAX_CACHE_BYTES = 2L * 1024L * 1024L // 2 MiB на запись
    }
}
