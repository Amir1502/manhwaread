package com.manhwaread.core.model

/**
 * Доменная модель главы. [season] и [chapterNumber] извлекаются из заголовка
 * парсером [ChapterNumberParser]; нераспознанный номер = [ChapterNumberParser.UNKNOWN].
 */
data class Chapter(
    val id: Long = 0L,
    val mangaId: Long,
    val url: String,
    val name: String,
    val season: Int = ChapterNumberParser.DEFAULT_SEASON,
    val chapterNumber: Float = ChapterNumberParser.UNKNOWN,
    val dateUploadMs: Long = 0L,
    val scanlator: String? = null,
    val read: Boolean = false,
) {
    /** true, если номер главы распознан (не -1f). */
    val isNumbered: Boolean get() = chapterNumber >= 0f
}
