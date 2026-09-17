package com.manhwaread.core.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmResponseValidatorTest {
    @Test
    fun `valid array yields Valid with segments`() {
        val raw = """[{"id":"s1","text":"привет"},{"id":"s2","text":"мир"}]"""
        val result = validate(raw, listOf("s1", "s2")) as ValidationResult.Valid
        assertEquals(listOf("s1", "s2"), result.segments.map { it.id })
        assertEquals("привет", result.segments[0].text)
    }

    @Test
    fun `segments are reordered to expected ids order`() {
        val raw = """[{"id":"s2","text":"мир"},{"id":"s1","text":"привет"}]"""
        val result = validate(raw, listOf("s1", "s2")) as ValidationResult.Valid
        assertEquals(listOf("s1", "s2"), result.segments.map { it.id })
    }

    @Test
    fun `object root with segments key accepted`() {
        val raw = """{"segments":[{"id":"s1","text":"привет"}]}"""
        val result = validate(raw, listOf("s1")) as ValidationResult.Valid
        assertEquals("привет", result.segments.single().text)
    }

    @Test
    fun `object root with translations key accepted`() {
        val raw = """{"translations":[{"id":"s1","text":"привет"}]}"""
        val result = validate(raw, listOf("s1")) as ValidationResult.Valid
        assertEquals(1, result.segments.size)
    }

    @Test
    fun `markdown code fences are stripped`() {
        val raw = "```json\n[{\"id\":\"s1\",\"text\":\"привет\"}]\n```"
        val result = validate(raw, listOf("s1")) as ValidationResult.Valid
        assertEquals("привет", result.segments.single().text)
    }

    @Test
    fun `surrounding whitespace tolerated`() {
        val raw = "\n\n  [{\"id\":\"s1\",\"text\":\"привет\"}]  \n"
        assertTrue(validate(raw, listOf("s1")) is ValidationResult.Valid)
    }

    @Test
    fun `missing id reported in Partial`() {
        val raw = """[{"id":"s1","text":"привет"}]"""
        val result = validate(raw, listOf("s1", "s2")) as ValidationResult.Partial
        assertEquals(listOf("s1"), result.segments.map { it.id })
        assertEquals(listOf("s2"), result.missingIds)
        assertTrue(result.unexpectedIds.isEmpty())
    }

    @Test
    fun `unexpected id reported in Partial`() {
        val raw = """[{"id":"s1","text":"привет"},{"id":"s9","text":"лишний"}]"""
        val result = validate(raw, listOf("s1")) as ValidationResult.Partial
        assertEquals(listOf("s1"), result.segments.map { it.id })
        assertTrue(result.missingIds.isEmpty())
        assertEquals(listOf("s9"), result.unexpectedIds)
    }

    @Test
    fun `missing and unexpected reported together`() {
        val raw = """[{"id":"s2","text":"мир"},{"id":"s3","text":"лишний"}]"""
        val result = validate(raw, listOf("s1", "s2")) as ValidationResult.Partial
        assertEquals(listOf("s2"), result.segments.map { it.id })
        assertEquals(listOf("s1"), result.missingIds)
        assertEquals(listOf("s3"), result.unexpectedIds)
    }

    @Test
    fun `unparseable json is Invalid`() {
        val result = validate("это совсем не json", listOf("s1")) as ValidationResult.Invalid
        assertTrue(result.reason.contains("JSON"))
    }

    @Test
    fun `root string is Invalid`() {
        assertTrue(validate("\"просто строка\"", listOf("s1")) is ValidationResult.Invalid)
    }

    @Test
    fun `object root without known key is Invalid`() {
        val raw = """{"foo":[{"id":"s1","text":"x"}]}"""
        assertTrue(validate(raw, listOf("s1")) is ValidationResult.Invalid)
    }

    @Test
    fun `element missing text is Invalid`() {
        val raw = """[{"id":"s1"}]"""
        assertTrue(validate(raw, listOf("s1")) is ValidationResult.Invalid)
    }

    @Test
    fun `element not object is Invalid`() {
        val raw = """["s1"]"""
        assertTrue(validate(raw, listOf("s1")) is ValidationResult.Invalid)
    }

    @Test
    fun `numeric id is Invalid`() {
        val raw = """[{"id":5,"text":"x"}]"""
        assertTrue(validate(raw, listOf("5")) is ValidationResult.Invalid)
    }

    @Test
    fun `blank text treated as missing`() {
        val raw = """[{"id":"s1","text":"  "},{"id":"s2","text":"мир"}]"""
        val result = validate(raw, listOf("s1", "s2")) as ValidationResult.Partial
        assertEquals(listOf("s2"), result.segments.map { it.id })
        assertEquals(listOf("s1"), result.missingIds)
    }

    @Test
    fun `duplicate id is Invalid`() {
        val raw = """[{"id":"s1","text":"a"},{"id":"s1","text":"b"}]"""
        val result = validate(raw, listOf("s1")) as ValidationResult.Invalid
        assertTrue(result.reason.contains("duplicate"))
    }

    @Test
    fun `empty expected and empty array is Valid`() {
        val result = validate("[]", emptyList()) as ValidationResult.Valid
        assertTrue(result.segments.isEmpty())
    }

    @Test
    fun `empty expected with items is Partial unexpected`() {
        val raw = """[{"id":"s1","text":"привет"}]"""
        val result = validate(raw, emptyList()) as ValidationResult.Partial
        assertTrue(result.segments.isEmpty())
        assertEquals(listOf("s1"), result.unexpectedIds)
    }

    @Test
    fun `unicode and escapes preserved`() {
        val raw = """[{"id":"s1","text":"он сказал \"привет\"\nи ушёл — 韓国語"}]"""
        val result = validate(raw, listOf("s1")) as ValidationResult.Valid
        assertEquals("он сказал \"привет\"\nи ушёл — 韓国語", result.segments.single().text)
    }
}
