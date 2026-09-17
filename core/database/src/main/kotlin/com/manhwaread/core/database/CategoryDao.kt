package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Категории библиотеки и связь «тайтл ↔ категория». */
@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun all(): List<CategoryEntity>

    // Системные категории неудаляемы: условие isSystem = 0 в самом запросе.
    @Query("DELETE FROM categories WHERE id = :id AND isSystem = 0")
    suspend fun deleteIfNotSystem(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMangaToCategory(ref: MangaCategoryCrossRef)

    @Query("DELETE FROM manga_category_refs WHERE mangaId = :mangaId AND categoryId = :categoryId")
    suspend fun removeMangaFromCategory(mangaId: Long, categoryId: Long)

    @Query(
        "SELECT manga.* FROM manga " +
            "INNER JOIN manga_category_refs ON manga.id = manga_category_refs.mangaId " +
            "WHERE manga_category_refs.categoryId = :categoryId ORDER BY manga.addedAtMs DESC",
    )
    fun observeMangaForCategory(categoryId: Long): Flow<List<MangaEntity>>
}
