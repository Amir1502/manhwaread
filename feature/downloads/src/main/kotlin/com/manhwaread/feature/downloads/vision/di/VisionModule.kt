package com.manhwaread.feature.downloads.vision.di

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.feature.downloads.vision.BitmapFactoryPageBitmapDecoder
import com.manhwaread.feature.downloads.vision.BubbleDetector
import com.manhwaread.feature.downloads.vision.BubbleStore
import com.manhwaread.feature.downloads.vision.Inpainter
import com.manhwaread.feature.downloads.vision.MlKitOcrEngine
import com.manhwaread.feature.downloads.vision.MultiLangOcrEngine
import com.manhwaread.feature.downloads.vision.OcrEngine
import com.manhwaread.feature.downloads.vision.OpenCvBubbleDetector
import com.manhwaread.feature.downloads.vision.OpenCvInpainter
import com.manhwaread.feature.downloads.vision.PageBitmapDecoder
import com.manhwaread.feature.downloads.vision.VisionChapterAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// DI стадии анализа: по умолчанию классический OpenCV-детектор баблов
// (ONNX-детектор подключается при наличии пользовательской модели) и
// мультязыковой ML Kit OCR (ko → ja → latin с дедупликацией).
@Module
@InstallIn(SingletonComponent::class)
object VisionModule {
    @Provides
    @Singleton
    fun provideBubbleDetector(): BubbleDetector = OpenCvBubbleDetector()

    @Provides
    @Singleton
    fun provideOcrEngine(): OcrEngine =
        MultiLangOcrEngine(
            listOf(
                MlKitOcrEngine(
                    DetectedLang.KO,
                    TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
                ),
                MlKitOcrEngine(
                    DetectedLang.JA,
                    TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
                ),
                MlKitOcrEngine(
                    DetectedLang.EN,
                    TextRecognition.getClient(TextRecognizerOptions.Builder().build()),
                ),
            ),
        )

    @Provides
    @Singleton
    fun provideInpainter(): Inpainter = OpenCvInpainter()

    @Provides
    @Singleton
    fun providePageBitmapDecoder(): PageBitmapDecoder = BitmapFactoryPageBitmapDecoder()

    // PageStore предоставляет QueueModule (ФАЗА 15): файловое хранилище
    // pages каталога главы вместо памяти процесса.

    @Provides
    @Singleton
    fun provideChapterAnalyzer(
        pageStore: PageStore,
        bitmapDecoder: PageBitmapDecoder,
        detector: BubbleDetector,
        ocr: OcrEngine,
        bubbleStore: BubbleStore,
    ): ChapterAnalyzer = VisionChapterAnalyzer(pageStore, bitmapDecoder, detector, ocr, bubbleStore = bubbleStore)
}
