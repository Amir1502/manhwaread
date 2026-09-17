package com.manhwaread.sources

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface JsonTransport {
    suspend fun get(url: HttpUrl): JsonObject
}

class SourceHttpException(val statusCode: Int) : IOException("Source returned HTTP $statusCode")
class SourceFormatException(message: String) : IOException(message)

object RetryDelay {
    fun parseMillis(value: String?, nowMillis: Long): Long? {
        if (value == null) return null
        val seconds = value.trim().toLongOrNull()
        if (seconds != null) return seconds.coerceIn(0, Long.MAX_VALUE / 1000) * 1000
        return runCatching {
            (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis)
                .coerceAtLeast(0)
        }.getOrNull()
    }
}

// Один экземпляр обслуживает один API-хост; изображения загружаются отдельным клиентом.
class RateLimitedJsonTransport(
    private val client: OkHttpClient,
    private val allowedHost: String,
    private val minimumIntervalMillis: Long = 250,
) : JsonTransport {
    private val permits = Semaphore(2)
    private val clockLock = Mutex()
    private var nextRequestAt = 0L
    private val json = Json { ignoreUnknownKeys = true }

    init { require(minimumIntervalMillis >= 0) }

    override suspend fun get(url: HttpUrl): JsonObject {
        require(url.isHttps && url.host == allowedHost)
        var lastFailure: IOException? = null
        repeat(4) { attempt ->
            val response = try {
                permits.withPermit {
                    waitForSlot()
                    execute(url)
                }
            } catch (error: IOException) {
                lastFailure = error
                if (attempt == 3) throw error
                delay(500L shl attempt)
                return@repeat
            }
            updateServerLimit(response.headers)
            if (response.code in 200..299) {
                val element = try {
                    json.parseToJsonElement(response.body)
                } catch (error: IllegalArgumentException) {
                    throw SourceFormatException("Source returned invalid JSON")
                }
                return element as? JsonObject ?: throw SourceFormatException("Expected a JSON object")
            }
            val failure = SourceHttpException(response.code)
            if ((response.code != 429 && response.code !in 500..599) || attempt == 3) throw failure
            lastFailure = failure
            val pause = RetryDelay.parseMillis(response.headers["Retry-After"], System.currentTimeMillis())
                ?: (500L shl attempt)
            // Длинное ограничение передаётся вызывающему коду вместо преждевременного повтора.
            if (pause > MAX_WAIT_MILLIS) throw failure
            deferRequests(pause)
        }
        throw lastFailure ?: IOException("Source request failed")
    }

    private suspend fun waitForSlot() = clockLock.withLock {
        val now = System.nanoTime()
        val waitNanos = (nextRequestAt - now).coerceAtLeast(0)
        if (waitNanos > 0) delay((waitNanos + 999_999) / 1_000_000)
        nextRequestAt = System.nanoTime() + minimumIntervalMillis * 1_000_000
    }

    private suspend fun deferRequests(millis: Long) = clockLock.withLock {
        nextRequestAt = maxOf(nextRequestAt, System.nanoTime() + millis * 1_000_000)
    }

    private suspend fun updateServerLimit(headers: Headers) {
        if (headers["X-RateLimit-Remaining"]?.toIntOrNull() != 0) return
        val seconds = headers["X-RateLimit-Retry-After"]?.toLongOrNull() ?: return
        if (seconds < 0 || seconds > Long.MAX_VALUE / 1000) return
        val wait = (seconds * 1000 - System.currentTimeMillis()).coerceAtLeast(0)
        if (wait > MAX_WAIT_MILLIS) throw SourceHttpException(429)
        deferRequests(wait)
    }

    private suspend fun execute(url: HttpUrl): HttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Manhwaread/0.1 (Android)")
                .header("Accept", "application/json")
                .header("Accept-Language", "ru,en;q=0.8")
                .build(),
        )
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val body = it.body ?: throw SourceFormatException("Source returned an empty body")
                        val source = body.source()
                        source.request(MAX_JSON_BYTES + 1)
                        if (source.buffer.size > MAX_JSON_BYTES) throw SourceFormatException("JSON response is too large")
                        HttpResponse(it.code, it.headers, source.buffer.readUtf8())
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private data class HttpResponse(val code: Int, val headers: Headers, val body: String)

    companion object {
        private const val MAX_JSON_BYTES = 8L * 1024 * 1024
        private const val MAX_WAIT_MILLIS = 300_000L
    }
}
