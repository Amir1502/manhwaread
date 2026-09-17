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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeepLProviderTest {
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

    private fun provider(apiKey: String = "deepl-key") =
        DeepLProvider(ProviderConfig(ProviderIds.DEEPL, apiKey, server.url("/v2").toString()), client)

    private fun request(vararg texts: String, lang: DetectedLang = DetectedLang.KO) = TranslationRequest(
        segments = texts.mapIndexed { index, text -> TranslatableSegment("s$index", text) },
        sourceLang = lang,
    )

    @Test
    fun `success maps translations back to segment ids`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"translations":[{"text":"Привет"},{"text":"Мир"}]}"""),
        )
        val result = provider().translate(request("Hello", "World", lang = DetectedLang.EN))
        val response = result.getOrNull()
        assertEquals("""[{"id":"s0","text":"Привет"},{"id":"s1","text":"Мир"}]""", response?.rawJson)

        val recorded = server.takeRequest()
        assertEquals("/v2/translate", recorded.path)
        assertEquals("DeepL-Auth-Key deepl-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"target_lang\":\"RU\""), body)
        assertTrue(body.contains("\"source_lang\":\"EN\""), body)
    }

    @Test
    fun `unknown source lang omits source_lang`() = runTest {
        server.enqueue(MockResponse().setBody("""{"translations":[{"text":"Привет"}]}"""))
        provider().translate(request("Hello", lang = DetectedLang.UNKNOWN))
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("source_lang"), body)
    }

    @Test
    fun `quota status 456 maps to ProviderQuota`() = runTest {
        server.enqueue(MockResponse().setResponseCode(456))
        val result = provider().translate(request("Hello"))
        assertEquals(AppError.ProviderQuota, (result as DomainResult.Failure).error)
    }

    @Test
    fun `translations array without text maps to ProviderBadResponse`() = runTest {
        server.enqueue(MockResponse().setBody("""{"translations":[{}]}"""))
        val result = provider().translate(request("Hello"))
        assertTrue((result as DomainResult.Failure).error is AppError.ProviderBadResponse)
    }

    @Test
    fun `empty batch fails without network call`() = runTest {
        val result = provider().translate(request(lang = DetectedLang.KO))
        assertTrue((result as DomainResult.Failure).error is AppError.ProviderBadResponse)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `supports russian target only`() {
        val deepl = provider()
        assertTrue(deepl.supports(DetectedLang.KO, TARGET_LANG_RU))
        assertFalse(deepl.supports(DetectedLang.KO, "en"))
    }

    @Test
    fun `blank api key maps to ProviderAuth without network call`() = runTest {
        val result = provider(apiKey = "").translate(request("Hello"))
        assertEquals(AppError.ProviderAuth, (result as DomainResult.Failure).error)
        assertEquals(0, server.requestCount)
    }
}
