package com.manhwaread.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class HeadersInterceptorTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun clientWith(interceptor: HeadersInterceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(interceptor).build()

    private fun executeGet(client: OkHttpClient): String {
        server.enqueue(MockResponse().setBody("ok"))
        val request = Request.Builder().url(server.url("/page")).build()
        return client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    @Test
    fun `adds user agent and accept language when absent`() {
        val body = executeGet(clientWith(HeadersInterceptor(userAgent = "Manhwaread-Test/1.0")))
        assertEquals("ok", body)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Manhwaread-Test/1.0", recorded.getHeader("User-Agent"))
        assertEquals("ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7", recorded.getHeader("Accept-Language"))
    }

    @Test
    fun `does not override explicit user agent`() {
        server.enqueue(MockResponse().setBody("ok"))
        val request = Request.Builder()
            .url(server.url("/page"))
            .header("User-Agent", "Custom/9.9")
            .build()
        clientWith(HeadersInterceptor("Manhwaread-Test/1.0")).newCall(request).execute().close()
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Custom/9.9", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `referer and origin added for configured domain`() {
        val referer = "http://${server.hostName}:${server.port}/"
        val interceptor = HeadersInterceptor("UA", refererByDomain = mapOf(server.hostName to referer))
        executeGet(clientWith(interceptor))
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals(referer, recorded.getHeader("Referer"))
        assertEquals("http://${server.hostName}:${server.port}", recorded.getHeader("Origin"))
    }

    @Test
    fun `referer not added for foreign domain`() {
        val interceptor = HeadersInterceptor("UA", refererByDomain = mapOf("source.test" to "https://source.test/"))
        executeGet(clientWith(interceptor))
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(recorded.getHeader("Referer"))
        assertNull(recorded.getHeader("Origin"))
    }

    @Test
    fun `subdomain matching is suffix-safe`() {
        val interceptor = HeadersInterceptor("UA", refererByDomain = mapOf("example.com" to "https://example.com/"))
        assertEquals("https://example.com/", interceptor.refererFor("example.com"))
        assertEquals("https://example.com/", interceptor.refererFor("sub.example.com"))
        assertNull(interceptor.refererFor("notexample.com"))
        assertNull(interceptor.refererFor("example.com.evil.org"))
    }
}
