package com.manhwaread.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope

// Пинч-зум и одиночный пан только в увеличенном состоянии: пока зум не начат,
// события НЕ потребляются — вертикальный скролл вебтуна и перелистывание
// пейджера продолжают работать.
suspend fun PointerInputScope.zoomAndPanGesture(
    isZoomedIn: () -> Boolean,
    onZoom: (factor: Float, focusX: Float, focusY: Float) -> Unit,
    onPan: (deltaX: Float, deltaY: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            when {
                zoomChange != ZOOM_NEUTRAL -> {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    onZoom(zoomChange, centroid.x, centroid.y)
                    event.changes.consumePositionChanges()
                }
                isZoomedIn() && panChange != Offset.Zero -> {
                    onPan(panChange.x, panChange.y)
                    event.changes.consumePositionChanges()
                }
            }
        } while (event.changes.any { change -> change.pressed })
    }
}

private fun List<PointerInputChange>.consumePositionChanges() {
    forEach { change ->
        if (change.position != change.previousPosition) {
            change.consume()
        }
    }
}

private const val ZOOM_NEUTRAL = 1f
