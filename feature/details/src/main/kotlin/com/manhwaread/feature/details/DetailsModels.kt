package com.manhwaread.feature.details

import com.manhwaread.core.common.AppError
import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.model.ChapterReadingOrderComparator
import com.manhwaread.core.model.NextChapterResolver
import com.manhwaread.core.model.NextChapterTarget
import com.manhwaread.core.model.ReadingStatus

enum class DetailsTab {
    INFO,
    CHAPTERS,
}

enum class ChapterSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

data class DetailsUiState(
    val manga: MangaEntity? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val message: DetailsMessage? = null,
    val selectedTab: DetailsTab = DetailsTab.INFO,
    val chapterQuery: String = "",
    val sortOrder: ChapterSortOrder = ChapterSortOrder.NEWEST_FIRST,
    val onlyUnread: Boolean = false,
) {
    val nextChapterTarget: NextChapterTarget
        get() = NextChapterResolver.resolve(chapters.map { it.toDomain() })

    val filteredChapters: List<ChapterEntity>
        get() {
            val query = chapterQuery.trim()
            val filtered = chapters.filter { chapter ->
                (!onlyUnread || !chapter.read) &&
                    (query.isEmpty() || chapter.name.contains(query, ignoreCase = true))
            }
            return when (sortOrder) {
                ChapterSortOrder.NEWEST_FIRST -> filtered.sortedWith { a, b ->
                    ChapterReadingOrderComparator.compare(b.toDomain(), a.toDomain())
                }
                ChapterSortOrder.OLDEST_FIRST -> filtered.sortedWith { a, b ->
                    ChapterReadingOrderComparator.compare(a.toDomain(), b.toDomain())
                }
            }
        }
}

// Одноразовые сообщения экрана (показываются в Snackbar; OpenReader — навигация).
sealed interface DetailsMessage {
    data class AddedToDownloads(val chapterName: String) : DetailsMessage
    data object AddedToLibrary : DetailsMessage
    data object RemovedFromLibrary : DetailsMessage

    // Открыть читалку по клику на главу: скачанная глава читается офлайн,
    // нескачанная стримится с источника на уровне приложения.
    data class OpenReader(val mangaId: Long, val chapterId: Long) : DetailsMessage
}

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class DetailsActions(
    val onBack: () -> Unit,
    val onToggleLibrary: () -> Unit,
    val onReadingStatusChange: (ReadingStatus?) -> Unit,
    val onTabSelected: (DetailsTab) -> Unit,
    val onChapterQueryChange: (String) -> Unit,
    val onToggleSortOrder: () -> Unit,
    val onToggleOnlyUnread: () -> Unit,
    val onChapterClick: (ChapterEntity) -> Unit,
    val onChapterDownload: (ChapterEntity) -> Unit,
    val onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
    val onRetry: () -> Unit,
    val onMessageShown: () -> Unit,
)
