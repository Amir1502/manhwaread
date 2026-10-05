package com.manhwaread.feature.reader

// Контекст главы, который читалке передаёт хост (:app): тайтл, оглавление,
// закладки и сохранённый режим. Значения по умолчанию — читалка без контекста
// тайтла (self-check, превью): кнопки глав, оглавление и закладка скрыты.

/** Пункт оглавления: глава тайтла; список в [ReaderNavigation.chapters] — в порядке чтения. */
data class ReaderTocItem(
    val chapterId: Long,
    val title: String,
    val isRead: Boolean = false,
)

/**
 * Данные хоста для панелей читалки. [chapters] упорядочены от первой главы
 * к последней; предыдущая/следующая глава вычисляются по [currentChapterId].
 * [preferredMode] — режим, сохранённый для тайтла (или вычисленный по типу).
 */
data class ReaderNavigation(
    val mangaTitle: String? = null,
    val chapters: List<ReaderTocItem> = emptyList(),
    val currentChapterId: Long? = null,
    val bookmarkedPages: Set<Int> = emptySet(),
    val preferredMode: ReaderMode? = null,
) {
    private val currentIndex: Int
        get() = chapters.indexOfFirst { item -> item.chapterId == currentChapterId }

    val currentChapter: ReaderTocItem?
        get() = chapters.getOrNull(currentIndex)

    val previousChapter: ReaderTocItem?
        get() = currentIndex.takeIf { index -> index > 0 }?.let { index -> chapters[index - 1] }

    val nextChapter: ReaderTocItem?
        get() = currentIndex.takeIf { index -> index >= 0 && index < chapters.lastIndex }
            ?.let { index -> chapters[index + 1] }

    /** Закладка доступна, только когда хост знает главу (есть куда сохранить). */
    val supportsBookmarks: Boolean
        get() = currentChapterId != null
}

/** Колбэки хоста: открыть другую главу, переключить закладку страницы, сохранить режим. */
data class ReaderNavigationActions(
    val onOpenChapter: (chapterId: Long) -> Unit = {},
    val onToggleBookmark: (pageIndex: Int) -> Unit = {},
    val onModeChange: (mode: ReaderMode) -> Unit = {},
)

/**
 * Подзаголовок верхней панели: «Том 7 Глава 302» → «Том 7 · Глава 302».
 * Название без явного тома («Глава 214 Конец») возвращается как есть
 * (с нормализацией пробелов).
 */
fun chapterSubtitle(name: String): String {
    val normalized = name.trim().replace(WHITESPACE_REGEX, " ")
    val match = VOLUME_CHAPTER_REGEX.matchEntire(normalized) ?: return normalized
    return "${match.groupValues[1]}$SUBTITLE_SEPARATOR${match.groupValues[2]}"
}

private const val SUBTITLE_SEPARATOR = " · "
private val WHITESPACE_REGEX = Regex("""\s+""")

// (?iu): регистронезависимо и для кириллицы («ТОМ», «том»).
private val VOLUME_CHAPTER_REGEX =
    Regex("""(?iu)((?:том|vol\.?|volume)\s*\d+(?:[.,]\d+)?)[\s.,:·–—-]+((?:глава|ch\.?|chapter)\s*\S.*)""")

// Сколько пунктов оставить над текущей главой при открытии: она видна
// сразу, без ручной прокрутки, и при этом не прижата к краю шторки.
private const val TOC_CONTEXT_ITEMS = 2

/**
 * Индекс первого видимого пункта оглавления, в котором главы идут от новых
 * к старым ([readingOrder] — от первой к последней): текущая глава оказывается
 * в видимой области с небольшим контекстом сверху.
 */
fun tocInitialScrollIndex(readingOrder: List<ReaderTocItem>, currentChapterId: Long?): Int {
    val indexInReadingOrder = readingOrder.indexOfFirst { item -> item.chapterId == currentChapterId }
    if (indexInReadingOrder < 0) return 0
    val indexNewestFirst = readingOrder.lastIndex - indexInReadingOrder
    return (indexNewestFirst - TOC_CONTEXT_ITEMS).coerceAtLeast(0)
}
