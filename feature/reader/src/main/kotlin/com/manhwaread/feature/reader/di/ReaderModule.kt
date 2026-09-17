package com.manhwaread.feature.reader.di

import com.manhwaread.feature.reader.BitmapFactoryImageSizeReader
import com.manhwaread.feature.reader.FileChapterLoader
import com.manhwaread.feature.reader.ImageSizeReader
import com.manhwaread.feature.reader.ReaderContentLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// DI читалки: файловый загрузчик глав — офлайн-путь (каталог главы в кэше,
// результат скачивания из ФАЗЫ 15).
@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {
    @Provides
    fun provideImageSizeReader(): ImageSizeReader = BitmapFactoryImageSizeReader()

    @Provides
    fun provideReaderContentLoader(imageSizeReader: ImageSizeReader): ReaderContentLoader =
        FileChapterLoader(imageSizeReader = imageSizeReader)
}
