package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.vision.DetectedLang
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GeminiProviderTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    // Тесты идут через локальный сервер: baseUrl подменяется в URL запроса.
    private fun provider(apiKey: String = "gem-key", model: String = "test-model") =
        GeminiProvider(ProviderConfig(ProviderIds.GEMINI, apiKey, server.url("/v1beta").toString(), model), client)

    private fun request() = TranslationRequest(
        segments = listOf(TranslatableSegment("s1", "Hello")),
        sourceLang = DetectedLang.EN,
    )

    @Test
    fun `success extracts candidate text`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"[{\"id\":\"s1\",\"text\":\"Привет\"}]"}]}}]}""",
            ),
        )
        val result = provider().translate(request())
        val response = result.getOrNull()
        assertEquals("""[{"id":"s1","text":"Привет"}]""", response?.rawJson)

        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/test-model:generateContent", recorded.path)
        assertEquals("gem-key", recorded.getHeader("x-goog-api-key"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("system_instruction"), body)
        assertTrue(body.contains("responseMimeType"), body)
    }

    @Test
    fun `403 maps to ProviderAuth`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val result = provider().translate(request())
        assertEquals(AppError.ProviderAuth, (result as DomainResult.Failure).error)
    }

    @Test
    fun `empty candidates map to ProviderBadResponse`() = runTest {
        server.enqueue(MockResponse().setBody("""{"candidates":[]}"""))
        val result = provider().translate(request())
        assertTrue((result as DomainResult.Failure).error is AppError.ProviderBadResponse)
    }

    @Test
    fun `blank api key maps to ProviderAuth without network call`() = runTest {
        val result = provider(apiKey = "").translate(request())
        assertEquals(AppError.ProviderAuth, (result as DomainResult.Failure).error)
        assertEquals(0, server.requestCount)
    }
}
