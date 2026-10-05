package com.manhwaread.core.database

import com.manhwaread.core.model.Manga

// Маппинг Manga ↔ MangaEntity.

internal fun MangaEntity.toDomain(): Manga = Manga(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genres,
    status = status,
    thumbnailUrl = thumbnailUrl,
    nsfw = nsfw,
    inLibrary = inLibrary,
    addedAtMs = addedAtMs,
    readingStatus = readingStatus,
    rating = rating,
    altTitle = altTitle,
    type = type,
    ageRating = ageRating,
    year = year,
    chapterCount = chapterCount,
)

internal fun Manga.toEntity(): MangaEntity = MangaEntity(
    id = id,
    sourceId = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genres,
    status = status,
    thumbnailUrl = thumbnailUrl,
    nsfw = nsfw,
    inLibrary = inLibrary,
    addedAtMs = addedAtMs,
    titleRu = null,
    readingStatus = readingStatus,
    rating = rating,
    altTitle = altTitle,
    type = type,
    ageRating = ageRating,
    year = year,
    chapterCount = chapterCount,
)
