package com.manhwaread.source.api

/**
 * Тайтл в терминах источника. Immutable: любые изменения — через copy().
 * Закреплённый контракт (AGENTS.md) — не менять.
 */
data class SManga(
    val url: String,
    val title: String,
    val sourceId: Long,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null,
    val nsfw: Boolean = false,
    val initialized: Boolean = false,
)
