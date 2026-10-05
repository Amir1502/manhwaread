package com.manhwaread.feature.reader

// Действие тапа мимо бабла: центральная треть экрана переключает панели,
// крайние трети листают (в вебтуне — прокручивают ленту на долю экрана).
enum class ReaderTapAction { TOGGLE_CHROME, PREVIOUS, NEXT }

/**
 * Зона тапа в координатах вьюпорта. Вертикальные режимы (вебтун, постранично
 * сверху вниз) делят экран на полосы: верхняя — назад, нижняя — вперёд.
 * Горизонтальные режимы делят экран на колонки; в «Справа налево» колонки
 * зеркальны (левая — вперёд). Центральная треть всегда переключает панели.
 */
fun resolveTapAction(
    x: Float,
    y: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    mode: ReaderMode,
): ReaderTapAction {
    if (viewportWidth <= 0f || viewportHeight <= 0f) return ReaderTapAction.TOGGLE_CHROME
    return if (mode.isVertical) {
        zoneOf(position = y, extent = viewportHeight, startAction = ReaderTapAction.PREVIOUS, endAction = ReaderTapAction.NEXT)
    } else if (mode.isRtl) {
        zoneOf(position = x, extent = viewportWidth, startAction = ReaderTapAction.NEXT, endAction = ReaderTapAction.PREVIOUS)
    } else {
        zoneOf(position = x, extent = viewportWidth, startAction = ReaderTapAction.PREVIOUS, endAction = ReaderTapAction.NEXT)
    }
}

private fun zoneOf(
    position: Float,
    extent: Float,
    startAction: ReaderTapAction,
    endAction: ReaderTapAction,
): ReaderTapAction {
    val third = extent / TAP_ZONE_COUNT
    return when {
        position < third -> startAction
        position > extent - third -> endAction
        else -> ReaderTapAction.TOGGLE_CHROME
    }
}

private const val TAP_ZONE_COUNT = 3f
