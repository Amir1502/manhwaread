package com.manhwaread.core.network

import com.manhwaread.core.common.AppError
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class CloudflareInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun request(): Request = Request.Builder().url(server.url("/protected")).build()

    private fun challengeResponse(code: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Server", "cloudflare")
        .setBody(body)

    @Test
    fun `noop solver turns challenge into CloudflareBlocked`() {
        server.enqueue(challengeResponse(403, "<html><title>Just a moment...</title><body>cf-chl jschl</body></html>"))
        client = OkHttpClient.Builder().addInterceptor(CloudflareInterceptor(NoopChallengeSolver)).build()

        val thrown = assertThrows(CloudflareBlockedException::class.java) {
            client.newCall(request()).execute().close()
        }
        assertEquals(AppError.CloudflareBlocked, thrown.asAppError())
        assertEquals(server.url("/protected").toString(), thrown.url)
    }

    @Test
    fun `solved challenge retried with cf_clearance cookie and solver UA`() {
        server.enqueue(challengeResponse(503, "<html>Just a moment...</html>"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("content"))
        val solver = object : CloudflareChallengeSolver {
            override suspend fun solve(url: String): CfClearance = CfClearance("TOKEN123", "SolverUA/2.0")
        }
        client = OkHttpClient.Builder().addInterceptor(CloudflareInterceptor(solver)).build()

        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("content", response.body?.string())
        }

        val first = server.takeRequest(5, TimeUnit.SECONDS)!!
        val second = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertNull(first.getHeader("Cookie"))
        assertEquals("cf_clearance=TOKEN123", second.getHeader("Cookie"))
        assertEquals("SolverUA/2.0", second.getHeader("User-Agent"))
    }

    @Test
    fun `stored clearance attached to later requests without challenge`() {
        val store = InMemoryCfCookieStore()
        store.put(server.hostName, CfClearance("SAVED", "SavedUA/1.0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("open"))
        client = OkHttpClient.Builder().addInterceptor(CloudflareInterceptor(NoopChallengeSolver, store)).build()

        client.newCall(request()).execute().use { assertEquals("open", it.body?.string()) }
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("cf_clearance=SAVED", recorded.getHeader("Cookie"))
        assertEquals("SavedUA/1.0", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `plain 403 without cloudflare markers passes through`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden by origin"))
        client = OkHttpClient.Builder().addInterceptor(CloudflareInterceptor(NoopChallengeSolver)).build()

        client.newCall(request()).execute().use { assertEquals(403, it.code) }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `403 with cloudflare server but clean body passes through`() {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "cloudflare").setBody("Access denied"))
        client = OkHttpClient.Builder().addInterceptor(CloudflareInterceptor(NoopChallengeSolver)).build()

        client.newCall(request()).execute().use { assertEquals(403, it.code) }
        assertEquals(1, server.requestCount)
    }
}
