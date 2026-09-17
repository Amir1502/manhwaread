package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpErrorMappingTest {
    @Test
    fun `401 and 403 map to ProviderAuth`() {
        assertEquals(AppError.ProviderAuth, httpErrorFor(401, null))
        assertEquals(AppError.ProviderAuth, httpErrorFor(403, null))
    }

    @Test
    fun `402 and deepl 456 map to ProviderQuota`() {
        assertEquals(AppError.ProviderQuota, httpErrorFor(402, null))
        assertEquals(AppError.ProviderQuota, httpErrorFor(456, null))
    }

    @Test
    fun `429 maps to RateLimited with retry-after seconds`() {
        assertEquals(AppError.RateLimited(7_000L), httpErrorFor(429, "7"))
    }

    @Test
    fun `429 without parsable header maps to RateLimited null`() {
        assertEquals(AppError.RateLimited(null), httpErrorFor(429, null))
        assertEquals(AppError.RateLimited(null), httpErrorFor(429, "Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun `other codes map to ProviderBadResponse with status`() {
        assertEquals(AppError.ProviderBadResponse("HTTP 500"), httpErrorFor(500, null))
        assertEquals(AppError.ProviderBadResponse("HTTP 400"), httpErrorFor(400, null))
    }

    @Test
    fun `parseRetryAfterMs trims seconds and rejects garbage`() {
        assertEquals(3_000L, parseRetryAfterMs(" 3 "))
        assertNull(parseRetryAfterMs("abc"))
        assertNull(parseRetryAfterMs(null))
    }

    @Test
    fun `provider config toString masks api key`() {
        val config = ProviderConfig(ProviderIds.OPENAI_COMPAT, "sk-secret-123", "https://api.example.com/v1")
        val text = config.toString()
        assertFalse(text.contains("sk-secret-123"), "apiKey must not leak into logs: $text")
        assertTrue(text.contains("***"))
    }
}
