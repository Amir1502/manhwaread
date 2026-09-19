package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.network.asAppError
import com.manhwaread.core.pipeline.ChapterDownloader
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.PageRef
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.Source
import com.manhwaread.source.api.SourceException
import com.manhwaread.source.api.SourceRegistry
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Стадия DOWNLOADING (ФАЗА 15): список страниц источника → байты в [PageStore] → PageRefs.
 * Уже сохранённые на диске страницы повторно не скачиваются: перевод переиспользует
 * скачанный растр, а после смерти процесса загрузка продолжается офлайн-страницами.
 * Целостность обязательна (фикс чёрных страниц): и кэш на диске, и тело ответа 200
 * проходят проверку [imageValidator]; не-растр считается отсутствующей страницей,
 * а неудача записи — ошибкой стадии, поэтому COMPLETED без валидных байтов невозможен.
 * [onProgress] сообщает прогресс и управляет отменой: false прерывает загрузку
 * (CancellationException перехватывает вызывающий слой).
 */
class SourceChapterDownloader(
    private val registry: SourceRegistry,
    private val client: OkHttpClient,
    private val pageStore: PageStore,
    private val onProgress: suspend (downloaded: Int, total: Int) -> Boolean = { _, _ -> true },
    // Прод-валидатор — BitmapFactory (isDecodableImage); JVM-тесты подставляют фейк.
    private val imageValidator: (ByteArray) -> Boolean = ::isDecodableImage,
) : ChapterDownloader {
    override suspend fun download(job: ChapterJob): DomainResult<List<PageRef>> {
        val source = registry.get(job.ref.sourceId)
            ?: return DomainResult.failure(AppError.SourceUnavailable)
        val listing = runCatching { source.getPageList(SChapter(url = job.ref.chapterUrl, name = "")) }
        val pages = listing.getOrElse { error -> return DomainResult.failure(mapSourceError(error)) }
        if (pages.isEmpty()) return DomainResult.failure(AppError.SourceLayoutChanged)
        return downloadPages(job, source, pages)
    }

    private suspend fun downloadPages(job: ChapterJob, source: Source, pages: List<Page>): DomainResult<List<PageRef>> {
        val refs = mutableListOf<PageRef>()
        for (page in pages) {
            val imageUrl = page.imageUrl ?: return DomainResult.failure(AppError.SourceLayoutChanged)
            // Кэш на диске доверяется только после валидации: битый или усечённый
            // файл считается отсутствующей страницей и перекачивается заново.
            val cached = pageStore.loadPage(job.ref.chapterId, page.index)
            if (cached == null || !imageValidator(cached)) {
                val bytes = fetch(imageUrl, source.baseUrl)
                    ?: return DomainResult.failure(
                        AppError.Network(IOException("page ${page.index}: fetch failed")),
                    )
                try {
                    pageStore.savePage(job.ref.chapterId, page.index, bytes)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (expected: IOException) {
                    // Атомарная запись не удалась (нет места, сбой rename): задача
                    // не должна завершиться COMPLETED с недостающей страницей.
                    return DomainResult.failure(AppError.StorageFull)
                }
            }
            refs += PageRef(index = page.index, url = imageUrl, referer = source.baseUrl)
            if (!onProgress(refs.size, pages.size)) {
                throw CancellationException("chapter ${job.ref.chapterId}: download cancelled")
            }
        }
        return DomainResult.success(refs)
    }

    // Источник переносит доменную ошибку в SourceException; прочее — сетевой маппинг.
    private fun mapSourceError(error: Throwable): AppError = when (error) {
        is SourceException -> error.error
        else -> error.asAppError()
    }

    private fun fetch(url: String, referer: String): ByteArray? {
        val request = Request.Builder().url(url).header(HEADER_REFERER, referer).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val bytes = if (response.isSuccessful) response.body?.bytes() else null
                // Антибот-страница с кодом 200: байты, которые не являются растром,
                // отклоняются — иначе pageN.bin молча доедет до COMPLETED.
                bytes?.takeIf { body -> imageValidator(body) }
            }
        }.getOrNull()
    }

    private companion object {
        const val HEADER_REFERER = "Referer"
    }
}
