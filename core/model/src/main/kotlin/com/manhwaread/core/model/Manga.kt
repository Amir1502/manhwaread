package com.manhwaread.core.model

import com.manhwaread.source.api.MangaStatus
import com.manhwaread.source.api.MangaType

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
    val readingStatus: ReadingStatus? = null,
    val rating: Float? = null,
    val altTitle: String? = null,
    val type: MangaType? = null,
    val ageRating: String? = null,
    val year: Int? = null,
    val chapterCount: Int? = null,
)
