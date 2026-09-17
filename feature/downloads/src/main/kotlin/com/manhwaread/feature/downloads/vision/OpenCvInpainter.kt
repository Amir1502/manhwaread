package com.manhwaread.feature.downloads.vision

import android.graphics.Bitmap
import com.manhwaread.core.vision.PointF
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

// Inpainting методом Telea: области исходного текста внутри баблов
// заполняются окружающим фоном. Используется для чистой подложки при
// экспорте/запекании; векторный оверлей перевода от него не зависит
// (ключевое архитектурное решение продукта).
class OpenCvInpainter(private val radiusPx: Double = DEFAULT_RADIUS_PX) : Inpainter {
    override fun inpaint(bitmap: Bitmap, textPolygons: List<List<PointF>>): Bitmap {
        if (!OpenCvInitializer.ensureLoaded()) {
            throw VisionException("OpenCV native library is not available")
        }
        val source = Mat()
        val mask = Mat(bitmap.height, bitmap.width, CvType.CV_8UC1, Scalar(0.0))
        val destination = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            for (polygon in textPolygons) {
                if (polygon.size < MIN_POLYGON_POINTS) {
                    continue
                }
                val points = polygon.map { vertex -> Point(vertex.x.toDouble(), vertex.y.toDouble()) }
                val contour = MatOfPoint()
                contour.fromList(points)
                Imgproc.fillPoly(mask, listOf(contour), Scalar(FILL_VALUE))
            }
            Photo.inpaint(source, mask, destination, radiusPx, Photo.INPAINT_TELEA)
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(destination, result)
            return result
        } finally {
            source.release()
            mask.release()
            destination.release()
        }
    }

    private companion object {
        const val DEFAULT_RADIUS_PX = 3.0
        const val MIN_POLYGON_POINTS = 3
        const val FILL_VALUE = 255.0
    }
}
