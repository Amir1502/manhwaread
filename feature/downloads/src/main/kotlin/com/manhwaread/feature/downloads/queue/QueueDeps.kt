package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.TranslationJobDao
import com.manhwaread.core.datastore.ApiKeyStore
import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.OverlayCompositor
import com.manhwaread.core.pipeline.OverlayStore
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.core.pipeline.SegmentStore
import com.manhwaread.core.translation.TranslationProviderFactory
import com.manhwaread.feature.downloads.vision.BubbleStore
import com.manhwaread.source.api.SourceRegistry
import okhttp3.OkHttpClient
import javax.inject.Inject

// Исполнители очереди (ФАЗА 15): стадии конвейера и хранилища.
// Параметр-объект против длинного конструктора (паттерн PipelineStages).
data class QueueComponents @Inject constructor(
    val registry: SourceRegistry,
    val httpClient: OkHttpClient,
    val pageStore: PageStore,
    val segmentStore: SegmentStore,
    val overlayStore: OverlayStore,
    val bubbleStore: BubbleStore,
    val analyzer: ChapterAnalyzer,
    val compositor: OverlayCompositor,
    val archiveWriter: ChapterArchiveWriter,
    val providerFactory: TranslationProviderFactory,
)

// Данные очереди: DAO задач/тайтлов и настройки перевода с ключами.
data class QueueData @Inject constructor(
    val taskDao: DownloadTaskDao,
    val jobDao: TranslationJobDao,
    val mangaDao: MangaDao,
    val chapterDao: ChapterDao,
    val settingsStore: SettingsStore,
    val apiKeyStore: ApiKeyStore,
)
