package com.manhwaread.core.database

import com.manhwaread.core.model.Manga

// Маппинг Manga ↔ MangaEntity; порядок positional-аргументов совпадает с объявлением полей.

internal fun MangaEntity.toDomain(): Manga =
    Manga(id, sourceId, url, title, artist, author, description, genres, status, thumbnailUrl, nsfw, inLibrary, addedAtMs)

internal fun Manga.toEntity(): MangaEntity =
    MangaEntity(id, sourceId, url, title, artist, author, description, genres, status, thumbnailUrl, nsfw, inLibrary, addedAtMs)
