package com.manhwaread.core.model

import com.manhwaread.source.api.MangaStatus

/**
 * Доменная модель тайтла в библиотеке пользователя.
 * [id] = 0 означает «ещё не сохранён в локальную БД».
 */
data class Manga(
    val id: Long = 0L,
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null,
    val nsfw: Boolean = false,
    val inLibrary: Boolean = false,
    val addedAtMs: Long = 0L,
)
