package com.manhwaread.feature.reader

import androidx.annotation.StringRes

// Режимы читалки: вебтун (непрерывная вертикальная лента), вертикальный
// постраничный, горизонтальные слева-направо и справа-налево (манга).
enum class ReaderMode {
    WEBTOON,
    VERTICAL,
    LTR,
    RTL,
}

// Вебтун: единый непрерывный скролл без границ страниц (швы запрещены).
val ReaderMode.isContinuousWebtoon: Boolean get() = this == ReaderMode.WEBTOON

// Вертикальные режимы (лента и постраничный).
val ReaderMode.isVertical: Boolean get() = this == ReaderMode.WEBTOON || this == ReaderMode.VERTICAL

// Справа налево: оригинальное направление чтения манги.
val ReaderMode.isRtl: Boolean get() = this == ReaderMode.RTL

// Ресурс человекочитаемого названия режима (нижняя панель экрана читалки).
@StringRes
fun ReaderMode.labelRes(): Int = when (this) {
    ReaderMode.WEBTOON -> R.string.reader_mode_webtoon
    ReaderMode.VERTICAL -> R.string.reader_mode_vertical
    ReaderMode.LTR -> R.string.reader_mode_ltr
    ReaderMode.RTL -> R.string.reader_mode_rtl
}
