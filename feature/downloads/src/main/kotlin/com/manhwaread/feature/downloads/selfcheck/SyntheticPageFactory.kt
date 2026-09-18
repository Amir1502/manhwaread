package com.manhwaread.feature.downloads.selfcheck

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream

// Синтетическая страница самопроверки: белый фон, контур бабла-эллипса
// и корейский текст внутри. Генерируется локально — без сети (офлайн DoD).
fun interface SyntheticPageFactory {
    fun createPages(): List<ByteArray>
}

class CanvasSyntheticPageFactory : SyntheticPageFactory {
    override fun createPages(): List<ByteArray> = listOf(createPage())

    private fun createPage(): ByteArray {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH_PX
        }
        canvas.drawOval(BUBBLE_RECT, bubblePaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = TEXT_SIZE_PX
        }
        canvas.drawText(BUBBLE_TEXT, TEXT_X_PX, TEXT_Y_PX, textPaint)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private companion object {
        const val PAGE_WIDTH = 400
        const val PAGE_HEIGHT = 600
        const val STROKE_WIDTH_PX = 4f
        const val TEXT_SIZE_PX = 32f
        const val TEXT_X_PX = 110f
        const val TEXT_Y_PX = 160f
        const val PNG_QUALITY = 100
        const val BUBBLE_TEXT = "안녕하세요"
        val BUBBLE_RECT = RectF(60f, 80f, 340f, 240f)
    }
}
