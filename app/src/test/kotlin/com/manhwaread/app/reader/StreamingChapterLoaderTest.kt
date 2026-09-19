package com.manhwaread.app.reader

import com.manhwaread.core.common.AppError
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.feature.reader.ChapterMeta
import com.manhwaread.feature.reader.ChapterMetaJson
import com.manhwaread.source.api.Filter
import com.manhwaread.source.api.InMemorySourceRegistry
import com.manhwaread.source.api.MangasPage
import com.manhwaread.source.api.Page
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga
import com.manhwaread.source.api.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// Стриминг главы без скачивания: страницы с источника → кэш-каталог формата
// FileChapterLoader (page%03d.* + chapter.json), ошибки → доменные AppError.
class StreamingChapterLoaderTest {
    @TempDir
    lateinit var tempDir: File

    private val server = MockWebServer()
    private val chapterDao = FakeChapterDao()
    private val mangaDao = FakeMangaDao()

    @BeforeEach
    fun setUp() {
        server.start()
        mangaDao.rows[MANGA_ID] = MangaEntity(id = MANGA_ID, sourceId = SOURCE_ID, url = "/manga/solo", title = "Solo Leveling")
        chapterDao.rows += ChapterEntity(id = CHAPTER_ID, mangaId = MANGA_ID, url = "/manga/solo/chapter-5", name = "Chapter 5")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun loader(pages: List<Page>, registerSource: Boolean = true): StreamingChapterLoader {
        val registry = InMemorySourceRegistry()
        if (registerSource) {
            registry.register(FakeSource(id = SOURCE_ID, pages = pages))
        }
        return StreamingChapterLoader(
            registry = registry,
            client = OkHttpClient(),
            chapterDao = chapterDao,
            mangaDao = mangaDao,
            streamRoot = tempDir,
            ioDispatcher = Dispatchers.IO,
        )
    }

    private fun chapterDir(): File = File(tempDir, "chapter_$CHAPTER_ID")

    private fun twoPages(): List<Page> = listOf(
        Page(index = 0, imageUrl = server.url("/p1.png").toString()),
        Page(index = 1, imageUrl = server.url("/p2.jpg").toString()),
    )

    private fun enqueueTwoImages() {
        server.enqueue(MockResponse().setBody(Buffer().write(PNG_BYTES)))
        server.enqueue(MockResponse().setBody(Buffer().write(JPEG_BYTES)))
    }

    @Test
    fun `streams pages into cache dir with chapter json`() = runTest {
        enqueueTwoImages()
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = loader(twoPages()).ensureStreamed(CHAPTER_ID) { done, total -> progress += done to total }

        val dir = chapterDir()
        assertEquals(dir, result.getOrNull())
        assertEquals(PNG_BYTES.toList(), File(dir, "page001.png").readBytes().toList())
        assertEquals(JPEG_BYTES.toList(), File(dir, "page002.jpg").readBytes().toList())
        assertEquals(
            ChapterMeta(title = "Chapter 5", bubbles = emptyList()),
            ChapterMetaJson.decode(File(dir, "chapter.json").readText()),
        )
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        // Каждый запрос несёт Referer источника (защита от хотлинка).
        assertEquals(SOURCE_BASE_URL, server.takeRequest().getHeader("Referer"))
        assertEquals(SOURCE_BASE_URL, server.takeRequest().getHeader("Referer"))
    }

    @Test
    fun `complete cache is reused without network`() = runTest {
        enqueueTwoImages()
        val loader = loader(twoPages())

        val first = loader.ensureStreamed(CHAPTER_ID) { _, _ -> }
        val second = loader.ensureStreamed(CHAPTER_ID) { _, _ -> }

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals(chapterDir(), second.getOrNull())
        // Два запроса — только первая загрузка; повтор кэша сеть не трогает.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `incomplete cache is re-downloaded from scratch`() = runTest {
        enqueueTwoImages()
        val loader = loader(twoPages())
        loader.ensureStreamed(CHAPTER_ID) { _, _ -> }
        // Имитация оборванного кэша: второй страницы нет.
        assertTrue(File(chapterDir(), "page002.jpg").delete())
        enqueueTwoImages()

        val result = loader.ensureStreamed(CHAPTER_ID) { _, _ -> }

        assertTrue(result.isSuccess)
        assertEquals(4, server.requestCount)
        assertEquals(PNG_BYTES.toList(), File(chapterDir(), "page001.png").readBytes().toList())
        assertEquals(JPEG_BYTES.toList(), File(chapterDir(), "page002.jpg").readBytes().toList())
    }

    @Test
    fun `http failure maps to network error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = loader(listOf(Page(index = 0, imageUrl = server.url("/p1.png").toString())))
            .ensureStreamed(CHAPTER_ID) { _, _ -> }

        assertTrue(result.errorOrNull() is AppError.Network)
        assertNull(result.getOrNull())
    }

    @Test
    fun `unknown image format fails as network error`() = runTest {
        server.enqueue(MockResponse().setBody("GIF89a — читалкой не поддерживается"))

        val result = loader(listOf(Page(index = 0, imageUrl = server.url("/p1.gif").toString())))
            .ensureStreamed(CHAPTER_ID) { _, _ -> }

        assertTrue(result.errorOrNull() is AppError.Network)
    }

    @Test
    fun `missing source fails with SourceUnavailable`() = runTest {
        val result = loader(pages = emptyList(), registerSource = false)
            .ensureStreamed(CHAPTER_ID) { _, _ -> }

        assertEquals(AppError.SourceUnavailable, result.errorOrNull())
    }

    @Test
    fun `unknown chapter fails with SourceUnavailable`() = runTest {
        val result = loader(twoPages()).ensureStreamed(chapterId = 999L) { _, _ -> }

        assertEquals(AppError.SourceUnavailable, result.errorOrNull())
    }

    @Test
    fun `empty page list fails with SourceLayoutChanged`() = runTest {
        val result = loader(pages = emptyList()).ensureStreamed(CHAPTER_ID) { _, _ -> }

        assertEquals(AppError.SourceLayoutChanged, result.errorOrNull())
    }

    @Test
    fun `page without url fails with SourceLayoutChanged`() = runTest {
        val result = loader(listOf(Page(index = 0, imageUrl = null)))
            .ensureStreamed(CHAPTER_ID) { _, _ -> }

        assertEquals(AppError.SourceLayoutChanged, result.errorOrNull())
    }

    // Источник-фейк: отдаёт заранее заданный список страниц.
    private class FakeSource(
        override val id: Long,
        private val pages: List<Page>,
    ) : Source {
        override val name: String = "Fake"
        override val lang: String = "en"
        override val baseUrl: String = SOURCE_BASE_URL
        override val supportsSearch: Boolean = true
        override val isNsfw: Boolean = false

        override suspend fun getPopular(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun getLatest(page: Int): MangasPage = MangasPage(emptyList(), false)
        override suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage =
            MangasPage(emptyList(), false)

        override suspend fun getDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = pages
    }

    private class FakeChapterDao : ChapterDao {
        val rows = mutableListOf<ChapterEntity>()

        override suspend fun insertIgnore(chapter: ChapterEntity): Long = chapter.id
        override suspend fun updateMetadata(
            mangaId: Long,
            url: String,
            name: String,
            season: Int,
            chapterNumber: Float,
            dateUploadMs: Long,
            scanlator: String?,
        ) = Unit

        override fun observeForManga(mangaId: Long): Flow<List<ChapterEntity>> =
            MutableStateFlow(rows.filter { it.mangaId == mangaId })

        override suspend fun allForManga(mangaId: Long): List<ChapterEntity> =
            rows.filter { it.mangaId == mangaId }

        override suspend fun findById(id: Long): ChapterEntity? = rows.firstOrNull { it.id == id }
        override suspend fun setRead(id: Long, read: Boolean) = Unit
        override suspend fun countForManga(mangaId: Long): Int = rows.count { it.mangaId == mangaId }
        override suspend fun deleteForManga(mangaId: Long) = Unit
    }

    private class FakeMangaDao : MangaDao {
        val rows = mutableMapOf<Long, MangaEntity>()

        override suspend fun upsert(manga: MangaEntity): Long = manga.id
        override suspend fun upsertAll(mangas: List<MangaEntity>): List<Long> = mangas.map { it.id }
        override suspend fun findById(id: Long): MangaEntity? = rows[id]
        override suspend fun findBySourceUrl(sourceId: Long, url: String): MangaEntity? =
            rows.values.firstOrNull { it.sourceId == sourceId && it.url == url }

        override fun observeLibrary(): Flow<List<MangaEntity>> =
            MutableStateFlow(rows.values.filter { it.inLibrary })

        override suspend fun searchInLibrary(query: String): List<MangaEntity> = emptyList()
        override suspend fun setInLibrary(id: Long, inLibrary: Boolean, nowMs: Long) = Unit

        // Русский тайтл стримингом не используется: фиксация в фейке не нужна.
        override suspend fun setTitleRu(id: Long, titleRu: String?) = Unit
        override suspend fun deleteById(id: Long) = Unit
    }

    private companion object {
        const val SOURCE_ID = 1L
        const val MANGA_ID = 1L
        const val CHAPTER_ID = 10L
        const val SOURCE_BASE_URL = "https://fake.test"
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 4, 5)
    }
}
