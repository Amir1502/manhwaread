package com.manhwaread.core.translation

/**
 * Результат валидации ответа LLM (КОНТРАКТ ФАЗЫ 6 — сигнатуры закреплены в AGENTS.md).
 * [Valid] — все ожидаемые сегменты переведены; [Partial] — состав расходится с запросом;
 * [Invalid] — структурный брак ответа (не JSON, не те поля, дубликаты id).
 */
sealed interface ValidationResult {
    data class Valid(val segments: List<TranslatedSegment>) : ValidationResult
    data class Partial(
        val segments: List<TranslatedSegment>,
        val missingIds: List<String>,
        val unexpectedIds: List<String>,
    ) : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}
