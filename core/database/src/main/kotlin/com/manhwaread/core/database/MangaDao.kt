package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** CRUD библиотеки: upsert по уникальному (sourceId, url), наблюдение, поиск. */
@Dao
interface MangaDao {
    @Upsert
    suspend fun upsert(manga: MangaEntity): Long

    // Room @Upsert разрешает конфликты по первичному ключу, а входящая сущность
    // имеет id = 0: подставляем id существующей строки, найденной по уникальному
    // индексу (sourceId, url). При обновлении @Upsert возвращает -1, поэтому
    // метод всегда отдаёт стабильный id строки.
    @Transaction
    suspend fun upsertBySourceUrl(manga: MangaEntity): Long {
        val existing = findBySourceUrl(manga.sourceId, manga.url)
        if (existing == null) {
            return upsert(manga)
        }
        upsert(manga.copy(id = existing.id))
        return existing.id
    }

    @Upsert
    suspend fun upsertAll(mangas: List<MangaEntity>): List<Long>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun findById(id: Long): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url")
    suspend fun findBySourceUrl(sourceId: Long, url: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE inLibrary = 1 ORDER BY addedAtMs DESC")
    fun observeLibrary(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE inLibrary = 1 AND title LIKE '%' || :query || '%' ORDER BY title ASC")
    suspend fun searchInLibrary(query: String): List<MangaEntity>

    // При добавлении в библиотеку фиксируем addedAtMs; при удалении — не трогаем.
    @Query(
        "UPDATE manga SET inLibrary = :inLibrary, " +
            "addedAtMs = CASE WHEN :inLibrary THEN :nowMs ELSE addedAtMs END " +
            "WHERE id = :id",
    )
    suspend fun setInLibrary(id: Long, inLibrary: Boolean, nowMs: Long)

    // Русский тайтл пишется точечным UPDATE: обновление строки источником
    // (upsertBySourceUrl) перевод не затирает, а сам он обновляется независимо.
    @Query("UPDATE manga SET titleRu = :titleRu WHERE id = :id")
    suspend fun setTitleRu(id: Long, titleRu: String?)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun deleteById(id: Long)
}
