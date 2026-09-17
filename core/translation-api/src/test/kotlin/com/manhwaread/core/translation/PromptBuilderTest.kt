package com.manhwaread.core.translation

import com.manhwaread.core.vision.DetectedLang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBuilderTest {
    private fun request(
        segments: List<TranslatableSegment>,
        sourceLang: DetectedLang = DetectedLang.KO,
        targetLang: String = TARGET_LANG_RU,
        contextHint: String? = null,
    ) = TranslationRequest(segments, sourceLang, targetLang, contextHint)

    @Test
    fun `system prompt demands strict json and russian by default`() {
        val prompt = TranslationPromptBuilder.systemPrompt()
        assertTrue(prompt.contains("Russian"))
        assertTrue(prompt.contains("JSON array"))
        assertTrue(prompt.contains("\"id\""))
        assertTrue(prompt.contains("\"text\""))
    }

    @Test
    fun `system prompt honors target language`() {
        assertTrue(TranslationPromptBuilder.systemPrompt("en").contains("English"))
        assertTrue(TranslationPromptBuilder.systemPrompt("ko").contains("Korean"))
    }

    @Test
    fun `user prompt lists ids and texts`() {
        val prompt = TranslationPromptBuilder.userPrompt(
            request(listOf(TranslatableSegment("s1", "안녕"), TranslatableSegment("s2", "세계"))),
        )
        assertTrue(prompt.contains("s1, s2"))
        assertTrue(prompt.contains("안녕"))
        assertTrue(prompt.contains("세계"))
    }

    @Test
    fun `user prompt escapes special characters`() {
        val prompt = TranslationPromptBuilder.userPrompt(
            request(listOf(TranslatableSegment("s1", "он сказал \"привет\""))),
        )
        assertTrue(prompt.contains("\\\"привет\\\""))
    }

    @Test
    fun `user prompt names source language`() {
        assertTrue(TranslationPromptBuilder.userPrompt(request(emptyList(), DetectedLang.JA)).contains("Japanese"))
        assertTrue(TranslationPromptBuilder.userPrompt(request(emptyList(), DetectedLang.UNKNOWN)).contains("unknown"))
    }

    @Test
    fun `sfx flag only for sound effects`() {
        val prompt = TranslationPromptBuilder.userPrompt(
            request(listOf(TranslatableSegment("s1", "бах", isSfx = true), TranslatableSegment("s2", "привет"))),
        )
        assertTrue(prompt.contains("\"sfx\":true"))
        assertEquals(1, prompt.split("\"sfx\"").size - 1)
    }

    @Test
    fun `context hint included only when present`() {
        val withHint = TranslationPromptBuilder.userPrompt(request(emptyList(), contextHint = "Глава 1: восхождение"))
        assertTrue(withHint.contains("Context: Глава 1: восхождение"))
        val without = TranslationPromptBuilder.userPrompt(request(emptyList()))
        assertFalse(without.contains("Context:"))
    }

    @Test
    fun `prompt building is deterministic`() {
        val segments = listOf(TranslatableSegment("s1", "текст"), TranslatableSegment("s2", "ещё", isSfx = true))
        val first = TranslationPromptBuilder.userPrompt(request(segments))
        val second = TranslationPromptBuilder.userPrompt(request(segments))
        assertEquals(first, second)
    }
}
