package com.manhwaread.core.translation

import com.manhwaread.core.vision.DetectedLang
import kotlinx.coroutines.CancellationException

/**
 * Перевод названия тайтла на русский через выбранного пользователем провайдера.
 * Результат пригоден для персистенции, только если это не эхо оригинала и он
 * содержит кириллицу; иначе (сбой провайдера, битый ответ, мусор вместо
 * перевода) возвращается null — UI показывает исходный тайтл источника.
 */
class TitleTranslator(private val provider: TranslationProvider) {
    // Один сегмент — один батч: переиспользуем валидацию ответа из BatchTranslator.
    private val batchTranslator = BatchTranslator(provider)

    /** Переводит [title] на русский; null — перевод не получен или непригоден. */
    suspend fun translate(title: String): String? {
        if (title.isBlank()) return null
        val translated = try {
            batchTranslator.translateAll(
                segments = listOf(TranslatableSegment(id = SEGMENT_ID, text = title)),
                sourceLang = DetectedLang.UNKNOWN,
                targetLang = TARGET_LANG_RU,
                contextHint = CONTEXT_HINT,
            ).getOrNull()?.firstOrNull { it.id == SEGMENT_ID }?.text
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Сбой провайдера не фатален: тайтл просто останется непереведённым.
            null
        }
        return translated?.trim()?.takeIf { isUsableTranslation(it, title) }
    }

    // Пригодный перевод: непустой, не эхо оригинала (регистр и серии пробелов
    // не учитываются), содержит хотя бы одну кириллицу.
    private fun isUsableTranslation(candidate: String, original: String): Boolean {
        if (candidate.isBlank()) return false
        if (normalizeForCompare(candidate).equals(normalizeForCompare(original), ignoreCase = true)) return false
        return candidate.any { it in CYRILLIC_RANGE }
    }

    private fun normalizeForCompare(text: String): String = text.trim().replace(WHITESPACE_RUNS, " ")

    private companion object {
        const val SEGMENT_ID = "title"
        const val CONTEXT_HINT = "Название манхвы/манги"
        val CYRILLIC_RANGE = '\u0400'..'\u04FF'
        val WHITESPACE_RUNS = Regex("\\s+")
    }
}
