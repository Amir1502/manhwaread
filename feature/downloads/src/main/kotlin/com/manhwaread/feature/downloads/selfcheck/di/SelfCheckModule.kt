package com.manhwaread.feature.downloads.selfcheck.di

import com.manhwaread.core.pipeline.OverlayCompositor
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.feature.downloads.selfcheck.CanvasSyntheticPageFactory
import com.manhwaread.feature.downloads.selfcheck.PipelineSelfCheck
import com.manhwaread.feature.downloads.selfcheck.SelfCheckAnalyzerFactory
import com.manhwaread.feature.downloads.selfcheck.SelfCheckExecutor
import com.manhwaread.feature.downloads.selfcheck.SyntheticPageFactory
import com.manhwaread.feature.downloads.vision.BubbleDetector
import com.manhwaread.feature.downloads.vision.BubbleStore
import com.manhwaread.feature.downloads.vision.OcrEngine
import com.manhwaread.feature.downloads.vision.PageBitmapDecoder
import com.manhwaread.feature.downloads.vision.VisionChapterAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// DI офлайн-самопроверки (ФАЗА 15): настоящий анализатор на синтетических
// страницах, настоящая типографика, эхо-перевод вместо сетевого провайдера.
@Module
@InstallIn(SingletonComponent::class)
object SelfCheckModule {
    @Provides
    @Singleton
    fun provideSelfCheckAnalyzerFactory(
        bitmapDecoder: PageBitmapDecoder,
        detector: BubbleDetector,
        ocr: OcrEngine,
        bubbleStore: BubbleStore,
    ): SelfCheckAnalyzerFactory = SelfCheckAnalyzerFactory { pageStore: PageStore ->
        VisionChapterAnalyzer(pageStore, bitmapDecoder, detector, ocr, bubbleStore = bubbleStore)
    }

    @Provides
    @Singleton
    fun provideSyntheticPageFactory(): SyntheticPageFactory = CanvasSyntheticPageFactory()

    @Provides
    @Singleton
    fun provideSelfCheckExecutor(
        analyzerFactory: SelfCheckAnalyzerFactory,
        compositor: OverlayCompositor,
        pageFactory: SyntheticPageFactory,
    ): SelfCheckExecutor = PipelineSelfCheck(analyzerFactory, compositor, pageFactory)
}
