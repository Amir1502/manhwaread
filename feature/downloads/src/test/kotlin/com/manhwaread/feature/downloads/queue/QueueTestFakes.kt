package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.database.SegmentDao
import com.manhwaread.core.database.SegmentEntity
import com.manhwaread.core.database.TranslationJobDao
import com.manhwaread.core.database.TranslationJobEntity
import com.manhwaread.core.datastore.ApiKeyStore
import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.datastore.TranslationSettings
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.OverlayCompositor
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.translation.TranslatedSegment
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment
import com.manhwaread.source.api.Filter
import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.Source
import com.manhwaread.source.api.SourceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Base64

// Общие фейки unit-тестов очереди (ФАЗА 15): DAO, настройки, источник, стадии.

// Настоящий 1×1 PNG: декодируемый растр для тестов целостности загрузок.
internal val onePixelPngBytes: ByteArray = Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
)

// Фейк валидации растра для JVM-тестов: настоящая проверка через BitmapFactory
// требует Android-среды (Robolectric), здесь достаточно магических байтов PNG/JPEG.
internal fun fakeImageValidator(bytes: ByteArray): Boolean =
    hasPrefix(bytes, PNG_MAGIC_BYTES) || hasPrefix(bytes, JPEG_MAGIC_BYTES)

private fun hasPrefix(bytes: ByteArray, prefix: ByteArray): Boolean =
    bytes.size >= prefix.size && prefix.indices.all { index -> bytes[index] == prefix[index] }

private val PNG_MAGIC_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
private val JPEG_MAGIC_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

internal class FakePageSource(
    override val id: Long = 7L,
    override val baseUrl: String,
) : Source {
    override val name = "Fake"
    override val lang = "en"
    override val supportsSearch = false
    override val isNsfw = false

    var pages: List<Page> = emptyList()
    var failure: AppError? = null
    var lastChapterUrl: String? = null
    var pageListCalls = 0
        private set

    override suspend fun getPopular(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getLatest(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage =
        MangasPage(emptyList(), false)

    override suspend fun getDetails(manga: SManga): SManga = manga

    override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        pageListCalls++
        lastChapterUrl = chapter.url
        failure?.let { error -> throw SourceException(error) }
        return pages
    }
}

internal class FakeDownloadTaskDao : DownloadTaskDao {
    val tasks = mutableListOf<DownloadTaskEntity>()
    private val flow = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())
    private var nextId = 1L

    // Отмена «на лету»: любой перевод в RUNNING сохраняется как CANCELLED.
    var cancelOnRunning = false

    fun seedTask(task: DownloadTaskEntity): Long {
        val id = if (task.id == 0L) nextId++ else task.id
        tasks += task.copy(id = id)
        publish()
        return id
    }

    override suspend fun upsert(task: DownloadTaskEntity): Long {
        val id = if (task.id == 0L) nextId++ else task.id
        replace(task.copy(id = id))
        return id
    }

    override suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Float) {
        val effective = if (cancelOnRunning && status == DownloadStatus.RUNNING) DownloadStatus.CANCELLED else status
        val existing = tasks.firstOrNull { it.id == id } ?: return
        val updated = if (effective == DownloadStatus.CANCELLED) {
            existing.copy(status = effective)
        } else {
            existing.copy(status = effective, progress = progress)
        }
        replace(updated)
    }

    override fun observeByStatus(status: DownloadStatus): Flow<List<DownloadTaskEntity>> =
        flow.map { all -> all.filter { it.status == status } }

    override suspend fun findById(id: Long): DownloadTaskEntity? = tasks.firstOrNull { it.id == id }

    override suspend fun latestForChapter(chapterId: Long): DownloadTaskEntity? =
        tasks.filter { it.chapterId == chapterId }.maxByOrNull { it.enqueuedAtMs }

    override fun observeAll(): Flow<List<DownloadTaskEntity>> = flow

    override suspend fun deleteByStatus(status: DownloadStatus) {
        tasks.removeAll { it.status == status }
        publish()
    }

    override suspend fun deleteForChapter(chapterId: Long) {
        tasks.removeAll { it.chapterId == chapterId }
        publish()
    }

    private fun replace(entity: DownloadTaskEntity) {
        val index = tasks.indexOfFirst { it.id == entity.id }
        if (index >= 0) tasks[index] = entity else tasks += entity
        publish()
    }

    private fun publish() {
        flow.value = tasks.toList()
    }
}

internal class FakeQueueMangaDao : MangaDao {
    val mangas = mutableMapOf<Long, MangaEntity>()

    override suspend fun upsert(manga: MangaEntity): Long {
        val id = if (manga.id == 0L) (mangas.keys.maxOrNull() ?: 0L) + 1 else manga.id
        mangas[id] = manga.copy(id = id)
        return id
    }

    override suspend fun upsertAll(mangasList: List<MangaEntity>): List<Long> = mangasList.map { manga -> upsert(manga) }

    override suspend fun findById(id: Long): MangaEntity? = mangas[id]

    override suspend fun findBySourceUrl(sourceId: Long, url: String): MangaEntity? =
        mangas.values.firstOrNull { it.sourceId == sourceId && it.url == url }

    override fun observeLibrary(): Flow<List<MangaEntity>> =
        MutableStateFlow(mangas.values.filter { it.inLibrary })

    override suspend fun searchInLibrary(query: String): List<MangaEntity> =
        mangas.values.filter { it.inLibrary && it.title.contains(query, ignoreCase = true) }

    override suspend fun setInLibrary(id: Long, inLibrary: Boolean, nowMs: Long) {
        mangas[id]?.let { manga -> mangas[id] = manga.copy(inLibrary = inLibrary, addedAtMs = nowMs) }
    }

    override suspend fun setTitleRu(id: Long, titleRu: String?) {
        mangas[id]?.let { manga -> mangas[id] = manga.copy(titleRu = titleRu) }
    }

    override suspend fun deleteById(id: Long) {
        mangas.remove(id)
    }
}

internal class FakeQueueChapterDao : ChapterDao {
    val chapters = mutableMapOf<Long, ChapterEntity>()

    override suspend fun insertIgnore(chapter: ChapterEntity): Long {
        val id = if (chapter.id == 0L) (chapters.keys.maxOrNull() ?: 0L) + 1 else chapter.id
        chapters.putIfAbsent(id, chapter.copy(id = id))
        return id
    }

    override suspend fun updateMetadata(
        mangaId: Long,
        url: String,
        name: String,
        season: Int,
        chapterNumber: Float,
        dateUploadMs: Long,
        scanlator: String?,
    ) {
        val existing = chapters.values.firstOrNull { it.mangaId == mangaId && it.url == url } ?: return
        chapters[existing.id] = existing.copy(
            name = name,
            season = season,
            chapterNumber = chapterNumber,
            dateUploadMs = dateUploadMs,
            scanlator = scanlator,
        )
    }

    override fun observeForManga(mangaId: Long): Flow<List<ChapterEntity>> =
        MutableStateFlow(chapters.values.filter { it.mangaId == mangaId })

    override suspend fun allForManga(mangaId: Long): List<ChapterEntity> =
        chapters.values.filter { it.mangaId == mangaId }

    override suspend fun findById(id: Long): ChapterEntity? = chapters[id]

    override suspend fun setRead(id: Long, read: Boolean) {
        chapters[id]?.let { chapter -> chapters[id] = chapter.copy(read = read) }
    }

    override suspend fun countForManga(mangaId: Long): Int = chapters.values.count { it.mangaId == mangaId }

    override suspend fun deleteForManga(mangaId: Long) {
        chapters.entries.removeAll { entry -> entry.value.mangaId == mangaId }
    }
}

internal class FakeQueueJobDao : TranslationJobDao {
    val entities = mutableMapOf<String, TranslationJobEntity>()

    override suspend fun upsertEntity(entity: TranslationJobEntity) {
        entities[entity.id] = entity
    }

    override suspend fun findEntityById(id: String): TranslationJobEntity? = entities[id]

    override suspend fun nextEntity(): TranslationJobEntity? =
        sortedEntities().firstOrNull { it.status == StageStatus.QUEUED }

    override suspend fun allEntities(): List<TranslationJobEntity> = sortedEntities()

    override fun observeAllEntities(): Flow<List<TranslationJobEntity>> = MutableStateFlow(sortedEntities())

    override suspend fun deleteForChapter(chapterId: Long) {
        entities.entries.removeAll { entry -> entry.value.chapterId == chapterId }
    }

    private fun sortedEntities(): List<TranslationJobEntity> =
        entities.values.sortedWith(compareByDescending<TranslationJobEntity> { it.priority }.thenBy { it.createdAt })
}

internal class FakeQueueSegmentDao : SegmentDao {
    val segments = mutableListOf<SegmentEntity>()
    val deletedChapters = mutableListOf<Long>()

    override suspend fun insertAll(segments: List<SegmentEntity>) {
        this.segments += segments
    }

    override suspend fun deleteForChapter(chapterId: Long) {
        deletedChapters += chapterId
        segments.removeAll { it.chapterId == chapterId }
    }

    override fun observeForChapter(chapterId: Long): Flow<List<SegmentEntity>> =
        MutableStateFlow(segments.filter { it.chapterId == chapterId })

    override suspend fun allForChapter(chapterId: Long): List<SegmentEntity> =
        segments.filter { it.chapterId == chapterId }

    override suspend fun updateTranslation(id: String, text: String, editedByUser: Boolean) {
        val index = segments.indexOfFirst { it.id == id }
        if (index >= 0) {
            segments[index] = segments[index].copy(translatedText = text, isEditedByUser = editedByUser, needsRetry = false)
        }
    }
}

internal class FakeQueueSettingsStore(initial: TranslationSettings = TranslationSettings()) : SettingsStore {
    private val settings = MutableStateFlow(initial)
    private val onboarding = MutableStateFlow(false)

    override val translationSettings: Flow<TranslationSettings> = settings

    override suspend fun updateTranslation(newSettings: TranslationSettings) {
        settings.value = newSettings
    }

    override val onboardingCompleted: Flow<Boolean> = onboarding

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboarding.value = completed
    }
}

internal class FakeQueueApiKeyStore : ApiKeyStore {
    val keys = mutableMapOf<String, String>()

    override fun saveApiKey(providerId: String, apiKey: String) {
        keys[providerId] = apiKey
    }

    override fun apiKey(providerId: String): String? = keys[providerId]

    override fun clearApiKey(providerId: String) {
        keys.remove(providerId)
    }
}

internal class FakeQueueAnalyzer : ChapterAnalyzer {
    var outcome: DomainResult<List<TextSegment>> = DomainResult.success(emptyList())
    var analyzeCalls = 0
        private set

    override suspend fun analyze(job: ChapterJob, pages: List<PageRef>): DomainResult<List<TextSegment>> {
        analyzeCalls++
        return outcome
    }
}

internal class FakeQueueCompositor : OverlayCompositor {
    var outcome: DomainResult<List<OverlaySpec>> = DomainResult.success(emptyList())
    var lastTranslated: List<TranslatedSegment> = emptyList()
        private set

    override suspend fun composite(
        job: ChapterJob,
        segments: List<TextSegment>,
        translated: List<TranslatedSegment>,
    ): DomainResult<List<OverlaySpec>> {
        lastTranslated = translated
        return outcome
    }
}
