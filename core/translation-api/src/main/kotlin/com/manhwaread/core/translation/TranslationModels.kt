package com.manhwaread.core.translation

import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.vision.DetectedLang

/** Целевой язык перевода по умолчанию (язык продукта — русский). */
const val TARGET_LANG_RU = "ru"

/** Валидированный сегмент перевода: id исходного сегмента + русский текст. */
data class TranslatedSegment(val id: String, val text: String)

/** Сегмент исходного текста, подготовленный к отправке провайдеру. */
data class TranslatableSegment(
    val id: String,
    val text: String,
    val isSfx: Boolean = false,
)

/** Запрос перевода батча сегментов. [contextHint] — тайтл/синопсис для консистентности терминов. */
data class TranslationRequest(
    val segments: List<TranslatableSegment>,
    val sourceLang: DetectedLang,
    val targetLang: String = TARGET_LANG_RU,
    val contextHint: String? = null,
)

/** Сырой ответ провайдера; перед использованием проходит [validate]. */
data class TranslationResponse(val rawJson: String)

/**
 * Контракт провайдера перевода (OpenAI-совместимый, Gemini, DeepL и др.).
 * Реализации подключаются в ФАЗЕ 12; ключи хранятся в Android Keystore.
 */
interface TranslationProvider {
    /** Стабильный идентификатор провайдера ("openai-compat", "gemini", "deepl"). */
    val id: String
    val displayName: String

    /** Поддерживает ли провайдер пару языков source→target. */
    fun supports(sourceLang: DetectedLang, targetLang: String): Boolean

    /** Переводит батч сегментов; успех — сырой JSON ответа для [validate]. */
    suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse>
}
