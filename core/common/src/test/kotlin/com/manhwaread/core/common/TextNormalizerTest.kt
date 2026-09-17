package com.manhwaread.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextNormalizerTest {
    @Test
    fun `nbsp replaced with regular space`() {
        assertEquals("Привет мир", TextNormalizer.stripInvisibles("Привет\u00A0мир"))
    }

    @Test
    fun `zero-width characters removed`() {
        assertEquals("слово", TextNormalizer.stripInvisibles("с\u200Bл\u200Cо\u200Dв\uFEFFо"))
    }

    @Test
    fun `soft hyphen removed`() {
        assertEquals("слово", TextNormalizer.stripInvisibles("сло\u00ADво"))
    }

    @Test
    fun `crlf normalized to lf`() {
        assertEquals("a\nb", TextNormalizer.stripInvisibles("a\r\nb"))
        assertEquals("a\nb", TextNormalizer.stripInvisibles("a\rb"))
    }

    @Test
    fun `cyrillic hyphenation joined without hyphen`() {
        assertEquals("перевод", TextNormalizer.collapseLineBreaks("пере-\nвод"))
    }

    @Test
    fun `particles keep hyphen across line break`() {
        assertEquals("из-за", TextNormalizer.collapseLineBreaks("из-\nза"))
        assertEquals("кто-нибудь", TextNormalizer.collapseLineBreaks("кто-\nнибудь"))
        assertEquals("что-то", TextNormalizer.collapseLineBreaks("что-\nто"))
    }

    @Test
    fun `latin compounds keep hyphen`() {
        assertEquals("well-known", TextNormalizer.collapseLineBreaks("well-\nknown"))
    }

    @Test
    fun `single newline inside sentence becomes space`() {
        assertEquals(
            "Он сказал. Она ответила.",
            TextNormalizer.collapseLineBreaks("Он сказал.\nОна ответила."),
        )
    }

    @Test
    fun `paragraph break preserved`() {
        assertEquals("Абзац.\n\nВторой.", TextNormalizer.collapseLineBreaks("Абзац.\n\nВторой."))
    }

    @Test
    fun `markdown bold underline and code removed`() {
        assertEquals(
            "жирный курсив код",
            TextNormalizer.stripMarkdown("**жирный** __курсив__ `код`"),
        )
    }

    @Test
    fun `markdown heading and quote markers removed`() {
        assertEquals("Заголовок", TextNormalizer.stripMarkdown("## Заголовок"))
        assertEquals("цитата", TextNormalizer.stripMarkdown("> цитата"))
    }

    @Test
    fun `speaker marker removed per line`() {
        assertEquals("Он пришёл", TextNormalizer.stripSpeakerMarkers("Narrator: Он пришёл"))
        assertEquals("Привет!", TextNormalizer.stripSpeakerMarkers("Итан:  Привет!"))
    }

    @Test
    fun `time with colon is not a speaker marker`() {
        assertEquals("10:30 встреча", TextNormalizer.stripSpeakerMarkers("10:30 встреча"))
    }

    @Test
    fun `multiple spaces collapse`() {
        assertEquals("а б", TextNormalizer.collapseWhitespace("а   б"))
    }

    @Test
    fun `space before punctuation removed`() {
        assertEquals("Привет, мир", TextNormalizer.collapseWhitespace("Привет , мир"))
    }

    @Test
    fun `space added after punctuation before letter`() {
        assertEquals("Привет, мир", TextNormalizer.collapseWhitespace("Привет,мир"))
    }

    @Test
    fun `digits after colon untouched`() {
        assertEquals("10:30", TextNormalizer.collapseWhitespace("10:30"))
    }

    @Test
    fun `straight quotes become yolkas`() {
        assertEquals(
            "Он сказал «привет».",
            TextNormalizer.russianTypography("Он сказал \"привет\"."),
        )
    }

    @Test
    fun `dialogue hyphen becomes em dash`() {
        assertEquals("— Привет", TextNormalizer.russianTypography("- Привет"))
    }

    @Test
    fun `inline dash becomes em dash`() {
        assertEquals("A — B", TextNormalizer.russianTypography("A - B"))
    }

    @Test
    fun `empty and blank inputs produce empty`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   \n  "))
    }

    @Test
    fun `speaker marker split across lines is joined then stripped`() {
        assertEquals("Он пришёл", TextNormalizer.normalize("Narrator:\nОн пришёл"))
    }

    @Test
    fun `dialogue lines stay separate`() {
        assertEquals("— Привет\n— Пока", TextNormalizer.normalize("- Привет\n- Пока"))
    }

    @Test
    fun `full pipeline composes all steps`() {
        val raw = "Narrator:\u00A0**Он** пришёл\u200B,\nи увидел - чудо.\n- Привет ,\u00A0мир"
        assertEquals("Он пришёл, и увидел — чудо.\n— Привет, мир", TextNormalizer.normalize(raw))
    }
}
