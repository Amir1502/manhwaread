package com.manhwaread.feature.downloads.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF

// Ошибка vision-движка (нативная библиотека не загружена, модель недоступна):
// анализатор страниц отображает её в AppError.VisionFailed.
class VisionException(message: String) : RuntimeException(message)

// Строка распознанного текста в координатах пикселей страницы.
data class OcrLine(
    val text: String,
    val bounds: RectF,
    val confidence: Float,
    val lang: DetectedLang,
    val readingOrder: Int = 0,
)

// Направление чтения: вебтун (сверху вниз) или страницы (RTL/LTR).
enum class ReadingDirection { VERTICAL_WEBTOON, RTL_PAGES, LTR_PAGES }

// Детекция облачков реплик на странице изображения.
interface BubbleDetector {
    suspend fun detect(bitmap: Bitmap, pageIndex: Int): List<Bubble>
}

// Распознавание текста на странице изображения.
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap, pageIndex: Int): List<OcrLine>
}

// Удаление оригинального текста внутри баблов (чистая подложка для
// экспорта/запекания; векторный оверлей перевода от inpainting не зависит).
interface Inpainter {
    fun inpaint(bitmap: Bitmap, textPolygons: List<List<PointF>>): Bitmap
}

// Декодирование байтов скачанной страницы в растр.
interface PageBitmapDecoder {
    fun decode(bytes: ByteArray): Bitmap?
}

class BitmapFactoryPageBitmapDecoder : PageBitmapDecoder {
    override fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
