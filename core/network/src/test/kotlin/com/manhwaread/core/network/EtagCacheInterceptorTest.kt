package com.manhwaread.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class EtagCacheInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().addInterceptor(EtagCacheInterceptor()).build()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun getOnce(path: String = "/data"): String {
        val request = Request.Builder().url(server.url(path)).build()
        return client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private fun getResponse(path: String = "/data"): Response {
        val request = Request.Builder().url(server.url(path)).build()
        return client.newCall(request).execute()
    }

    @Test
    fun `etag cached and 304 served from memory as 200`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"v1\"").setBody("payload"))
        server.enqueue(MockResponse().setResponseCode(304))

        assertEquals("payload", getOnce())
        getResponse().use { response ->
            assertEquals(200, response.code)
            assertEquals("hit", response.header("X-Etag-Cache"))
            assertEquals("payload", response.body?.string())
        }

        val first = server.takeRequest(5, TimeUnit.SECONDS)!!
        val second = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(first.getHeader("If-None-Match"))
        assertEquals("\"v1\"", second.getHeader("If-None-Match"))
    }

    @Test
    fun `response without etag is not cached`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("a"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("b"))

        assertEquals("a", getOnce())
        assertEquals("b", getOnce())

        server.takeRequest(5, TimeUnit.SECONDS)
        val second = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(second.getHeader("If-None-Match"))
    }

    @Test
    fun `cached entry contains etag bytes and content type`() {
        val store = InMemoryEtagCacheStore()
        val localClient = OkHttpClient.Builder().addInterceptor(EtagCacheInterceptor(store)).build()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "W/\"v2\"")
                .setHeader("Content-Type", "text/plain")
                .setBody("hello"),
        )
        val url = server.url("/d")
        localClient.newCall(Request.Builder().url(url).build()).execute().close()

        val cached = store.get(url.toString())
        assertEquals("W/\"v2\"", cached?.etag)
        assertArrayEquals("hello".toByteArray(), cached?.bytes)
        assertEquals("text/plain", cached?.contentType)
    }

    @Test
    fun `lru store evicts oldest beyond capacity`() {
        val store = InMemoryEtagCacheStore(maxEntries = 2)
        store.put("a", CachedBody("1", byteArrayOf(1), null))
        store.put("b", CachedBody("2", byteArrayOf(2), null))
        store.put("c", CachedBody("3", byteArrayOf(3), null))

        assertNull(store.get("a"))
        assertEquals("2", store.get("b")?.etag)
        assertEquals("3", store.get("c")?.etag)
    }
}
