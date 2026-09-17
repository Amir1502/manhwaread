package com.manhwaread.source.api

/**
 * Глава в терминах источника. [url] — относительный путь от baseUrl источника.
 * Закреплённый контракт (AGENTS.md) — не менять.
 */
data class SChapter(
    val url: String,
    val name: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
    val read: Boolean = false,
)
