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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenAiCompatProviderTest {
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

    private fun config(apiKey: String = "sk-test", model: String? = "test-model") =
        ProviderConfig(ProviderIds.OPENAI_COMPAT, apiKey, server.url("/v1").toString(), model)

    private fun provider(config: ProviderConfig = config()) = OpenAiCompatProvider(config, client)

    private fun request() = TranslationRequest(
        segments = listOf(TranslatableSegment("s1", "Hello")),
        sourceLang = DetectedLang.EN,
    )

    @Test
    fun `success extracts message content and posts chat completions`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"content":"[{\"id\":\"s1\",\"text\":\"Привет\"}]"}}]}"""),
        )
        val result = provider().translate(request())
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals("""[{"id":"s1","text":"Привет"}]""", response!!.rawJson)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"test-model\""), body)
        assertTrue(body.contains("\"role\":\"system\""), body)
        assertTrue(body.contains("manhwa"), body)
    }

    @Test
    fun `blank model falls back to default`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"choices":[{"message":{"content":"[]"}}]}"""),
        )
        provider(config(model = "")).translate(request())
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains(OpenAiCompatProvider.DEFAULT_MODEL), body)
    }

    @Test
    fun `401 maps to ProviderAuth`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("denied"))
        val result = provider().translate(request())
        assertTrue(result is DomainResult.Failure)
        assertEquals(AppError.ProviderAuth, (result as DomainResult.Failure).error)
    }

    @Test
    fun `429 maps to RateLimited with retry-after`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5"))
        val result = provider().translate(request())
        assertEquals(AppError.RateLimited(5_000L), (result as DomainResult.Failure).error)
    }

    @Test
    fun `500 maps to ProviderBadResponse`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = provider().translate(request())
        assertEquals(AppError.ProviderBadResponse("HTTP 500"), (result as DomainResult.Failure).error)
    }

    @Test
    fun `missing baseUrl fails without network call`() = runTest {
        val broken = OpenAiCompatProvider(ProviderConfig(ProviderIds.OPENAI_COMPAT, "sk", null), client)
        val result = broken.translate(request())
        assertTrue((result as DomainResult.Failure).error is AppError.ProviderBadResponse)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `blank api key maps to ProviderAuth without network call`() = runTest {
        val result = provider(config(apiKey = " ")).translate(request())
        assertEquals(AppError.ProviderAuth, (result as DomainResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `malformed response maps to ProviderBadResponse`() = runTest {
        server.enqueue(MockResponse().setBody("""{"unexpected":true}"""))
        val result = provider().translate(request())
        assertTrue((result as DomainResult.Failure).error is AppError.ProviderBadResponse)
    }

    @Test
    fun `connection failure maps to Network`() = runTest {
        server.shutdown()
        val result = provider().translate(request())
        assertTrue((result as DomainResult.Failure).error is AppError.Network)
    }
}
