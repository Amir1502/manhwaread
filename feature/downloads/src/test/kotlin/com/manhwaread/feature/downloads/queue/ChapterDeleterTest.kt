package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.database.DownloadTaskEntity
import com.manhwaread.core.database.SegmentEntity
import com.manhwaread.core.database.TranslationJobEntity
import com.manhwaread.core.vision.DetectedLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// Удаление скачанной главы: каталог файлов целиком и строки трёх таблиц БД,
// чужие главы не затрагиваются; отсутствующий каталог — не ошибка.
class ChapterDeleterTest {
    @TempDir
    lateinit var tempDir: File

    private val dirs by lazy { ChapterDirs(tempDir) }
    private val taskDao = FakeDownloadTaskDao()
    private val jobDao = FakeQueueJobDao()
    private val segmentDao = FakeQueueSegmentDao()

    private fun deleter() = ChapterDeleter(dirs, taskDao, jobDao, segmentDao, Dispatchers.Unconfined)

    private fun seedFiles(chapterId: Long): File {
        val dir = dirs.ensureDirFor(chapterId)
        File(dir, "page001.png").writeBytes(byteArrayOf(1, 2))
        File(dir, "chapter.json").writeText("{}")
        val nested = File(dir, "nested").apply { mkdirs() }
        File(nested, "deep.bin").writeBytes(byteArrayOf(3))
        return dir
    }

    private fun seedTask(chapterId: Long): Long =
        taskDao.seedTask(DownloadTaskEntity(mangaId = 1L, chapterId = chapterId, enqueuedAtMs = chapterId))

    private suspend fun seedJob(chapterId: Long) {
        jobDao.upsertEntity(
            TranslationJobEntity(
                id = "translate-$chapterId",
                sourceId = 1L,
                mangaId = 1L,
                chapterId = chapterId,
                chapterUrl = "/ch/$chapterId",
            ),
        )
    }

    private fun segment(id: String, chapterId: Long) = SegmentEntity(
        id = id,
        chapterId = chapterId,
        bubbleId = "b-$id",
        pageIndex = 0,
        ocrText = "안녕",
        ocrLang = DetectedLang.KO,
        ocrConfidence = 1f,
        readingOrder = 0,
    )

    @Test
    fun `delete removes chapter directory recursively`() = runTest {
        val dir = seedFiles(10L)
        assertTrue(File(dir, "nested/deep.bin").isFile)

        deleter().delete(10L)

        assertFalse(dir.exists())
    }

    @Test
    fun `delete removes db rows of the chapter only`() = runTest {
        seedFiles(10L)
        seedFiles(20L)
        seedTask(10L)
        val keptTaskId = seedTask(20L)
        seedJob(10L)
        seedJob(20L)
        segmentDao.insertAll(listOf(segment("s10", 10L), segment("s20", 20L)))

        deleter().delete(10L)

        assertFalse(dirs.dirFor(10L).exists())
        assertTrue(dirs.dirFor(20L).isDirectory)
        assertEquals(listOf(keptTaskId), taskDao.tasks.map { it.id })
        assertEquals(setOf("translate-20"), jobDao.entities.keys)
        assertEquals(listOf("s20"), segmentDao.segments.map { it.id })
        assertEquals(listOf(10L), segmentDao.deletedChapters)
    }

    @Test
    fun `delete of missing chapter is safe`() = runTest {
        deleter().delete(999L)

        assertFalse(dirs.dirFor(999L).exists())
        assertTrue(taskDao.tasks.isEmpty())
        assertTrue(jobDao.entities.isEmpty())
        assertTrue(segmentDao.deletedChapters.contains(999L))
    }
}
