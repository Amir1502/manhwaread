package com.manhwaread.source.api

/**
 * Тип тайтла: Манга (Япония), Манхва (Корея), Маньхуа (Китай) или Другое (OEL/комиксы/веб-комиксы).
 */
enum class MangaType {
    MANGA,
    MANHWA,
    MANHUA,
    OTHER,
    ;

    companion object {
        /**
         * Нечувствительный к регистру разбор типа из строки источника.
         */
        fun fromString(value: String?): MangaType? {
            if (value.isNullOrBlank()) return null
            val normalized = value.trim().lowercase()
            return when {
                "манхва" in normalized || "manhwa" in normalized -> MANHWA
                "манга" in normalized || "manga" in normalized -> MANGA
                "маньхуа" in normalized || "manhua" in normalized -> MANHUA
                else -> OTHER
            }
        }
    }
}
