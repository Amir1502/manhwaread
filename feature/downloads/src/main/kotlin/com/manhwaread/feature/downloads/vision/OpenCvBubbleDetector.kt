package com.manhwaread.feature.downloads.vision

import android.graphics.Bitmap
import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

// Классическая CV-детекция баблов: яркие (белые) области бинаризуются,
// морфологическое закрыние склеивает разрывы, внешние контуры фильтруются
// чистой функцией toBubbles. Работает без ML-модели — базовый путь;
// ONNX-детектор (OnnxBubbleDetector) подключается при наличии модели.
class OpenCvBubbleDetector(
    private val binaryThreshold: Double = DEFAULT_BINARY_THRESHOLD,
    private val closeKernelPx: Int = DEFAULT_CLOSE_KERNEL_PX,
    private val maxDetectWidthPx: Int = DEFAULT_MAX_DETECT_WIDTH,
) : BubbleDetector {
    override suspend fun detect(bitmap: Bitmap, pageIndex: Int): List<Bubble> =
        withContext(Dispatchers.Default) {
            if (!OpenCvInitializer.ensureLoaded()) {
                throw VisionException("OpenCV native library is not available")
            }
            val source = Mat()
            val gray = Mat()
            val scaled = Mat()
            val binary = Mat()
            val hierarchy = Mat()
            try {
                Utils.bitmapToMat(bitmap, source)
                Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
                val factor = minOf(1f, maxDetectWidthPx.toFloat() / bitmap.width.coerceAtLeast(1))
                if (factor < 1f) {
                    Imgproc.resize(gray, scaled, Size(), factor.toDouble(), factor.toDouble(), Imgproc.INTER_AREA)
                } else {
                    gray.copyTo(scaled)
                }
                Imgproc.threshold(scaled, binary, binaryThreshold, BINARY_MAX_VALUE, Imgproc.THRESH_BINARY)
                val kernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE,
                    Size(closeKernelPx.toDouble(), closeKernelPx.toDouble()),
                )
                Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)
                val contours = ArrayList<MatOfPoint>()
                Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                val inverseFactor = 1f / factor
                val rawContours = contours
                    .map { matOfPoint -> toRawContour(matOfPoint) }
                    .map { contour -> scaleContour(contour, inverseFactor, inverseFactor) }
                toBubbles(rawContours, bitmap.width, bitmap.height, pageIndex)
            } finally {
                source.release()
                gray.release()
                scaled.release()
                binary.release()
                hierarchy.release()
            }
        }

    private fun toRawContour(matOfPoint: MatOfPoint): RawContour {
        val points = matOfPoint.toArray().map { point -> PointF(point.x.toFloat(), point.y.toFloat()) }
        return RawContour(points, Imgproc.contourArea(matOfPoint))
    }

    private companion object {
        const val DEFAULT_BINARY_THRESHOLD = 200.0
        const val BINARY_MAX_VALUE = 255.0
        const val DEFAULT_CLOSE_KERNEL_PX = 9
        const val DEFAULT_MAX_DETECT_WIDTH = 1024
    }
}
