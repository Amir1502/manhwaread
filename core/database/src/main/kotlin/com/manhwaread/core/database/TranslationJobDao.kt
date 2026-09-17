package com.manhwaread.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterJobStore
import com.manhwaread.core.pipeline.JobState

/** Персистентная очередь задач конвейера; реализует [ChapterJobStore] из :core:pipeline. */
@Dao
interface TranslationJobDao : ChapterJobStore {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntity(entity: TranslationJobEntity)

    @Query("SELECT * FROM translation_jobs WHERE id = :id")
    suspend fun findEntityById(id: String): TranslationJobEntity?

    // Литерал 'QUEUED' = StageStatus.QUEUED.name: конвертер хранит имена перечислений.
    @Query("SELECT * FROM translation_jobs WHERE status = 'QUEUED' ORDER BY priority DESC, createdAt ASC LIMIT 1")
    suspend fun nextEntity(): TranslationJobEntity?

    @Query("SELECT * FROM translation_jobs ORDER BY priority DESC, createdAt ASC")
    suspend fun allEntities(): List<TranslationJobEntity>

    override suspend fun enqueue(job: ChapterJob) {
        upsertEntity(job.toEntity())
    }

    override suspend fun update(id: String, state: JobState) {
        val existing = findEntityById(id) ?: return
        upsertEntity(existing.copy(status = state.status, attempts = state.attempts, lastError = appErrorToString(state.lastError)))
    }

    override suspend fun findById(id: String): ChapterJob? = findEntityById(id)?.toDomain()

    override suspend fun next(): ChapterJob? = nextEntity()?.toDomain()

    override suspend fun all(): List<ChapterJob> = allEntities().map { it.toDomain() }
}
