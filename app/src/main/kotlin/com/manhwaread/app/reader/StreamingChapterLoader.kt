package com.manhwaread.app.reader

import android.content.Context
import com.manhwaread.app.di.IoDispatcher
import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.network.asAppError
import com.manhwaread.feature.reader.ChapterMeta
import com.manhwaread.feature.reader.ChapterMetaJson
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.Source
import com.manhwaread.source.api.SourceException
import com.manhwaread.source.api.SourceRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

// Квалификатор корневого каталога стримингового кэша (cacheDir/stream).
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamCacheDir

@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {
    // Каталог стриминговых глав: cacheDir/stream/chapter_{id} — тот же формат,
    // что у офлайн-глав (page%03d.* + chapter.json), поэтому FileChapterLoader
    // из :feature:reader читает его без изменений.
    @Provides
    @Singleton
    @StreamCacheDir
    fun provideStreamCacheDir(
        @ApplicationContext context: Context,
    ): File = File(context.cacheDir, STREAM_ROOT_DIR)

    private const val STREAM_ROOT_DIR = "stream"
}

/**
 * Стриминг главы без скачивания: страницы последовательно загружаются
 * с источника в кэш-каталог `stream/chapter_{id}`, после чего глава
 * открывается той же файловой читалкой, что и скачанная. Перевод не
 * выполняется — chapter.json пишется с пустыми баблами. Полный кэш
 * переиспользуется при повторном открытии без обращений к сети.
 */
@Singleton
class StreamingChapterLoader @Inject constructor(
    private val registry: SourceRegistry,
    private val client: OkHttpClient,
    private val chapterDao: ChapterDao,
    private val mangaDao: MangaDao,
    @StreamCacheDir private val streamRoot: File,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Гарантирует, что глава доступна как каталог файлов: возвращает готовый
     * каталог либо доменную ошибку. [onProgress] вызывается после сохранения
     * каждой страницы (сколько сохранено, всего страниц).
     */
    suspend fun ensureStreamed(chapterId: Long, onProgress: (done: Int, total: Int) -> Unit): DomainResult<File> =
        withContext(ioDispatcher) {
            val chapter = chapterDao.findById(chapterId)
            val manga = chapter?.let { found -> mangaDao.findById(found.mangaId) }
            val source = manga?.let { found -> registry.get(found.sourceId) }
            if (chapter == null || source == null) {
                // Нет строки главы/тайтла в БД или источник не подключён.
                DomainResult.failure(AppError.SourceUnavailable)
            } else {
                streamChapter(chapter, source, onProgress)
            }
        }

    private suspend fun streamChapter(
        chapter: ChapterEntity,
        source: Source,
        onProgress: (done: Int, total: Int) -> Unit,
    ): DomainResult<File> {
        val pages = when (val listing = pageListOf(source, chapter)) {
            is DomainResult.Failure -> return listing
            is DomainResult.Success -> listing.value
        }
        val dir = File(streamRoot, "$CHAPTER_DIR_PREFIX${chapter.id}")
        if (countPageImages(dir) >= pages.size) {
            // Кэш уже полный: сеть не трогаем, метаданные лишь освежаем.
            writeChapterMeta(dir, chapter)
            return DomainResult.success(dir)
        }
        return downloadPages(chapter, source, pages, dir, onProgress)
    }

    // Список страниц источника: SourceException несёт доменную ошибку, прочие
    // сбои маппятся в сетевые. Пустой список или страница без URL — вёрстка
    // источника изменилась.
    private suspend fun pageListOf(source: Source, chapter: ChapterEntity): DomainResult<List<Page>> {
        val pages = runCatching { source.getPageList(SChapter(url = chapter.url, name = chapter.name)) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                return DomainResult.failure(mapSourceError(error))
            }
        return if (pages.isEmpty() || pages.any { page -> page.imageUrl == null }) {
            DomainResult.failure(AppError.SourceLayoutChanged)
        } else {
            DomainResult.success(pages)
        }
    }

    // Источник переносит доменную ошибку в SourceException; прочее — сетевой маппинг.
    private fun mapSourceError(error: Throwable): AppError = when (error) {
        is SourceException -> error.error
        else -> error.asAppError()
    }

    private suspend fun downloadPages(
        chapter: ChapterEntity,
        source: Source,
        pages: List<Page>,
        dir: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ): DomainResult<File> {
        // Незавершённый кэш неполон: начинаем с чистого каталога.
        dir.deleteRecursively()
        dir.mkdirs()
        var done = 0
        for (page in pages) {
            currentCoroutineContext().ensureActive()
            val url = page.imageUrl ?: return DomainResult.failure(AppError.SourceLayoutChanged)
            val bytes = fetch(url, source.baseUrl)
                ?: return DomainResult.failure(AppError.Network(IOException("page ${page.index}: fetch failed")))
            val extension = extensionFor(bytes)
                ?: return DomainResult.failure(
                    AppError.Network(IOException("page ${page.index}: unsupported image format")),
                )
            File(dir, "${pageName(page.index)}.$extension").writeBytes(bytes)
            done += 1
            onProgress(done, pages.size)
        }
        writeChapterMeta(dir, chapter)
        return DomainResult.success(dir)
    }

    private fun fetch(url: String, referer: String): ByteArray? {
        val request = Request.Builder().url(url).header(HEADER_REFERER, referer).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
    }

    // Минимальный chapter.json для FileChapterLoader: заголовок и пустые
    // баблы — стриминговые главы читаются без перевода.
    private fun writeChapterMeta(dir: File, chapter: ChapterEntity) {
        dir.mkdirs()
        val meta = ChapterMeta(title = chapter.name, bubbles = emptyList())
        File(dir, CHAPTER_META_FILE).writeText(ChapterMetaJson.encode(meta))
    }

    private fun countPageImages(dir: File): Int =
        dir.listFiles { file -> file.isFile && file.extension.lowercase(Locale.US) in PAGE_EXTENSIONS }?.size ?: 0

    // page001 — нумерация с 1 и три цифры: FileChapterLoader сортирует по числу в имени.
    private fun pageName(pageIndex: Int): String = String.format(Locale.US, "page%03d", pageIndex + 1)

    // Расширение по магическим байтам; неизвестный формат — ошибка: читалка
    // понимает только png/jpg/webp, битый файл оставлять в кэше нельзя.
    private fun extensionFor(bytes: ByteArray): String? = when {
        bytes.startsWith(PNG_MAGIC) -> "png"
        bytes.startsWith(JPEG_MAGIC) -> "jpg"
        isWebp(bytes) -> "webp"
        else -> null
    }

    private fun isWebp(bytes: ByteArray): Boolean =
        bytes.size > WEBP_HEADER_END &&
            bytes.startsWith(RIFF_MAGIC) &&
            String(bytes, WEBP_MARKER_OFFSET, WEBP_MARKER_LENGTH, Charsets.US_ASCII) == WEBP_MARKER

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private companion object {
        const val CHAPTER_DIR_PREFIX = "chapter_"
        const val CHAPTER_META_FILE = "chapter.json"
        const val HEADER_REFERER = "Referer"
        val PAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
        const val WEBP_MARKER = "WEBP"
        const val WEBP_MARKER_OFFSET = 8
        const val WEBP_MARKER_LENGTH = 4
        const val WEBP_HEADER_END = 12
    }
}
