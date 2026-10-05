package com.manhwaread.app.navigation

import com.manhwaread.feature.reader.ReaderMode
import com.manhwaread.source.api.MangaType

// Режим читалки по умолчанию по типу тайтла (спека Этапа 8): манга читается
// справа налево, манхва, маньхуа и тайтлы неизвестного типа — вебтун-лентой.
fun defaultReaderModeFor(type: MangaType?): ReaderMode = when (type) {
    MangaType.MANGA -> ReaderMode.RTL
    MangaType.MANHWA, MangaType.MANHUA, MangaType.OTHER, null -> ReaderMode.WEBTOON
}

// Имя режима из DataStore → ReaderMode; неизвестное имя (режим переименован
// или удалён) → null, и тогда действует режим по умолчанию для типа тайтла.
fun readerModeOf(name: String?): ReaderMode? = ReaderMode.entries.firstOrNull { mode -> mode.name == name }
