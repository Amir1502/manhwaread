package com.manhwaread.core.translation

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.vision.DetectedLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

// Фейк провайдера: копит запросы, отвечает через handler (результат или исключение).
private class FakeTitleProvider(
    private val handler: (TranslationRequest) -> DomainResult<TranslationResponse>,
) : TranslationProvider {
    override val id: String = "fake"
    override val displayName: String = "Fake Title Provider"
    val requests = mutableListOf<TranslationRequest>()

    override fun supports(sourceLang: DetectedLang, targetLang: String): Boolean = true

    override suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse> {
        requests += request
        return handler(request)
    }
}

// Ответ провайдера с переводом единственного сегмента "title".
private fun titleResponse(text: String) =
    DomainResult.success(TranslationResponse("""[{"id":"title","text":"$text"}]"""))

class TitleTranslatorTest {
    @Test
    fun `valid russian translation is returned`() = runBlocking {
        val translator = TitleTranslator(FakeTitleProvider { titleResponse("Поднятие уровня в одиночку") })
        assertEquals("Поднятие уровня в одиночку", translator.translate("Solo Leveling"))
    }

    @Test
    fun `result is trimmed before returning`() = runBlocking {
        val translator = TitleTranslator(FakeTitleProvider { titleResponse("  Всеведущий читатель  ") })
        assertEquals("Всеведущий читатель", translator.translate("Omniscient Reader"))
    }

    @Test
    fun `echo of original is rejected`() = runBlocking {
        val translator = TitleTranslator(FakeTitleProvider { titleResponse(" solo   LEVELING ") })
        assertNull(translator.translate("Solo Leveling"))
    }

    @Test
    fun `latin-only translation is rejected`() = runBlocking {
        val translator = TitleTranslator(FakeTitleProvider { titleResponse("Solo Levelling Remake") })
        assertNull(translator.translate("Solo Leveling"))
    }

    @Test
    fun `provider failure yields null`() = runBlocking {
        val translator = TitleTranslator(FakeTitleProvider { DomainResult.failure(AppError.Network(IOException("boom"))) })
        assertNull(translator.translate("Solo Leveling"))
    }

    @Test
    fun `malformed provider json yields null`() = runBlocking {
        val translator = TitleTranslator(FakeTitleProvider { DomainResult.success(TranslationResponse("мусор")) })
        assertNull(translator.translate("Solo Leveling"))
    }

    @Test
    fun `missing title segment yields null`() = runBlocking {
        val provider = FakeTitleProvider {
            DomainResult.success(TranslationResponse("""[{"id":"other","text":"Поднятие"}]"""))
        }
        assertNull(TitleTranslator(provider).translate("Solo Leveling"))
    }

    @Test
    fun `provider exception yields null`() = runBlocking {
        val translator = TitleTranslator(FakeTitleProvider { throw IllegalStateException("provider crashed") })
        assertNull(translator.translate("Solo Leveling"))
    }

    @Test
    fun `cancellation is rethrown not swallowed`() {
        val translator = TitleTranslator(FakeTitleProvider { throw CancellationException("cancelled") })
        assertThrows(CancellationException::class.java) {
            runBlocking { translator.translate("Solo Leveling") }
        }
    }

    @Test
    fun `blank title short-circuits without provider call`() = runBlocking {
        val provider = FakeTitleProvider { error("provider must not be called") }
        val translator = TitleTranslator(provider)
        assertNull(translator.translate("   "))
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun `request carries title segment russian target and context hint`() = runBlocking {
        val provider = FakeTitleProvider { titleResponse("Берсерк") }
        assertEquals("Берсерк", TitleTranslator(provider).translate("Berserk"))
        val sent = provider.requests.single()
        assertEquals(DetectedLang.UNKNOWN, sent.sourceLang)
        assertEquals(TARGET_LANG_RU, sent.targetLang)
        assertEquals("Название манхвы/манги", sent.contextHint)
        assertEquals(listOf(TranslatableSegment(id = "title", text = "Berserk")), sent.segments)
    }
}
