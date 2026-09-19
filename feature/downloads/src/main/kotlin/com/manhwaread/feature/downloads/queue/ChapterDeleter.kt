package com.manhwaread.feature.downloads.queue

import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.SegmentDao
import com.manhwaread.core.database.TranslationJobDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Удаление скачанной главы (экран «Загрузки»): каталог файлов главы
// и все связанные строки БД — задачи скачивания, задачи перевода, OCR-сегменты.
// Сама глава (chapters) сохраняется: к ней привязаны история и закладки (FK-каскад).
class ChapterDeleter(
    private val chapterDirs: ChapterDirs,
    private val taskDao: DownloadTaskDao,
    private val jobDao: TranslationJobDao,
    private val segmentDao: SegmentDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Конструктор для Hilt: зависимости есть в графе (ChapterDirs — @Singleton,
    // DAO — из DatabaseModule), диспетчер получает значение по умолчанию.
    // Не @Inject на основном конструкторе: в графе нет биндинга CoroutineDispatcher.
    @Inject
    constructor(
        chapterDirs: ChapterDirs,
        taskDao: DownloadTaskDao,
        jobDao: TranslationJobDao,
        segmentDao: SegmentDao,
    ) : this(chapterDirs, taskDao, jobDao, segmentDao, Dispatchers.IO)

    suspend fun delete(chapterId: Long) {
        withContext(ioDispatcher) {
            // Каталог удаляется со всем содержимым; отсутствующий каталог — не ошибка.
            chapterDirs.dirFor(chapterId).deleteRecursively()
        }
        // Строки экрана исчезают реактивно: Flow DAO переопубликуют очереди.
        taskDao.deleteForChapter(chapterId)
        jobDao.deleteForChapter(chapterId)
        segmentDao.deleteForChapter(chapterId)
    }
}
