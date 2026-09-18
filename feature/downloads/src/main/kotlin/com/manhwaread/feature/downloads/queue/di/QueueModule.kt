package com.manhwaread.feature.downloads.queue.di

import android.content.Context
import com.manhwaread.core.database.RoomSegmentStore
import com.manhwaread.core.database.SegmentDao
import com.manhwaread.core.pipeline.OverlayCompositor
import com.manhwaread.core.pipeline.OverlayStore
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.core.pipeline.SegmentStore
import com.manhwaread.core.vision.TextMeasurer
import com.manhwaread.feature.downloads.queue.ChapterArchiveWriter
import com.manhwaread.feature.downloads.queue.ChapterDirs
import com.manhwaread.feature.downloads.queue.FileOverlayStore
import com.manhwaread.feature.downloads.queue.FilePageStore
import com.manhwaread.feature.downloads.queue.QueueComponents
import com.manhwaread.feature.downloads.queue.QueueData
import com.manhwaread.feature.downloads.queue.QueueProcessor
import com.manhwaread.feature.downloads.vision.BubbleStore
import com.manhwaread.feature.downloads.vision.InMemoryBubbleStore
import com.manhwaread.feature.downloads.vision.PaintTextMeasurer
import com.manhwaread.feature.downloads.vision.TypesettingCompositor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

// DI очереди и офлайн-хранилищ (ФАЗА 15): страницы и оверлеи живут в
// filesDir/chapters — каталогах, которые читает читалка (FileChapterLoader).
@Module
@InstallIn(SingletonComponent::class)
object QueueModule {
    @Provides
    @Singleton
    fun provideChapterDirs(
        @ApplicationContext context: Context,
    ): ChapterDirs =
        ChapterDirs(File(context.filesDir, CHAPTERS_DIR_NAME))

    // Продакшен-хранилище страниц: файл на диске вместо памяти процесса.
    @Provides
    @Singleton
    fun providePageStore(chapterDirs: ChapterDirs): PageStore = FilePageStore(chapterDirs)

    @Provides
    @Singleton
    fun provideBubbleStore(): BubbleStore = InMemoryBubbleStore()

    @Provides
    @Singleton
    fun provideOverlayStore(chapterDirs: ChapterDirs): OverlayStore = FileOverlayStore(chapterDirs)

    @Provides
    @Singleton
    fun provideSegmentStore(segmentDao: SegmentDao): SegmentStore = RoomSegmentStore(segmentDao)

    @Provides
    @Singleton
    fun provideTextMeasurer(): TextMeasurer = PaintTextMeasurer()

    @Provides
    @Singleton
    fun provideOverlayCompositor(bubbleStore: BubbleStore, measurer: TextMeasurer): OverlayCompositor =
        TypesettingCompositor(bubbleStore, measurer)

    @Provides
    @Singleton
    fun provideChapterArchiveWriter(chapterDirs: ChapterDirs): ChapterArchiveWriter =
        ChapterArchiveWriter(chapterDirs)

    @Provides
    @Singleton
    fun provideQueueProcessor(components: QueueComponents, data: QueueData): QueueProcessor =
        QueueProcessor(components, data)

    private const val CHAPTERS_DIR_NAME = "chapters"
}
