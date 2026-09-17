package com.manhwaread.feature.downloads.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import com.manhwaread.core.vision.Bubble
import com.manhwaread.core.vision.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.FloatBuffer

// Детектор баблов на пользовательской ONNX-модели сегментации
// (вход RGB NCHW [1,3,S,S], выход — логиты маски [1,1,S,S]).
// Файл модели в APK не входит и подключается пользователем; без модели
// детектор честно сообщает об ошибке, DI по умолчанию выбирает OpenCV-путь.
class OnnxBubbleDetector(
    private val modelFile: File,
    private val inputSizePx: Int = DEFAULT_INPUT_SIZE,
    private val maskThreshold: Float = DEFAULT_MASK_THRESHOLD,
    private val normMean: Float = DEFAULT_NORM_MEAN,
    private val normStd: Float = DEFAULT_NORM_STD,
) : BubbleDetector {
    @Volatile
    private var cachedSession: OrtSession? = null

    override suspend fun detect(bitmap: Bitmap, pageIndex: Int): List<Bubble> =
        withContext(Dispatchers.Default) {
            if (!modelFile.isFile) {
                throw VisionException("ONNX model not found: ${modelFile.name}")
            }
            if (!OpenCvInitializer.ensureLoaded()) {
                throw VisionException("OpenCV native library is not available")
            }
            val environment = OrtEnvironment.getEnvironment()
            val session = cachedSession ?: environment.createSession(modelFile.absolutePath).also { created ->
                cachedSession = created
            }
            val inputName = session.inputNames.first()
            val maskMat = Mat()
            try {
                val inputMat = resizeToSquareRgb(bitmap)
                val tensorData = matToNchw(inputMat, normMean, normStd)
                inputMat.release()
                val shape = longArrayOf(1L, CHANNELS_RGB.toLong(), inputSizePx.toLong(), inputSizePx.toLong())
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(tensorData), shape).use { tensor ->
                    session.run(mapOf(inputName to tensor)).use { result ->
                        val logits = flattenFirstOutput(result[0].value)
                        val mask = decodeMask(logits, inputSizePx, inputSizePx, maskThreshold)
                        fillMaskMat(mask, maskMat)
                    }
                }
                val contours = ArrayList<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(maskMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                hierarchy.release()
                val rawContours = contours.map { matOfPoint -> toRawContour(matOfPoint) }
                val factorX = bitmap.width.toFloat() / inputSizePx
                val factorY = bitmap.height.toFloat() / inputSizePx
                toBubbles(
                    raw = rawContours.map { contour -> scaleContour(contour, factorX, factorY) },
                    pageWidth = bitmap.width,
                    pageHeight = bitmap.height,
                    pageIndex = pageIndex,
                )
            } finally {
                maskMat.release()
            }
        }

    private fun resizeToSquareRgb(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val rgb = Mat()
        val resized = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        Imgproc.resize(rgb, resized, Size(inputSizePx.toDouble(), inputSizePx.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        rgba.release()
        rgb.release()
        return resized
    }

    // HWC-байты RGB Мата → планарный NCHW-тензор с нормализацией.
    private fun matToNchw(mat: Mat, mean: Float, std: Float): FloatArray {
        val planeSize = mat.width() * mat.height()
        val hwc = ByteArray(planeSize * CHANNELS_RGB)
        mat.get(0, 0, hwc)
        val output = FloatArray(planeSize * CHANNELS_RGB)
        for (index in 0 until planeSize) {
            val pixelOffset = index * CHANNELS_RGB
            output[index] = normalizeChannel((hwc[pixelOffset].toInt() and BYTE_MASK).toFloat(), mean, std)
            output[planeSize + index] =
                normalizeChannel((hwc[pixelOffset + 1].toInt() and BYTE_MASK).toFloat(), mean, std)
            output[planeSize * 2 + index] =
                normalizeChannel((hwc[pixelOffset + 2].toInt() and BYTE_MASK).toFloat(), mean, std)
        }
        return output
    }

    // VERIFY-API: ORT-Java возвращает float-тензор [1,1,S,S] вложенными
    // массивами по числу измерений; склеиваем строки маски в плоский массив.
    @Suppress("UNCHECKED_CAST")
    private fun flattenFirstOutput(value: Any): FloatArray {
        val nested = value as Array<Array<Array<FloatArray>>>
        return nested[0][0].reduce { accumulator, row -> accumulator + row }
    }

    private fun fillMaskMat(mask: BooleanArray, destination: Mat) {
        destination.create(inputSizePx, inputSizePx, CvType.CV_8UC1)
        val bytes = ByteArray(mask.size) { index -> if (mask[index]) MASK_FOREGROUND else 0 }
        destination.put(0, 0, bytes)
    }

    private fun toRawContour(matOfPoint: MatOfPoint): RawContour {
        val points = matOfPoint.toArray().map { point -> PointF(point.x.toFloat(), point.y.toFloat()) }
        return RawContour(points, Imgproc.contourArea(matOfPoint))
    }

    private companion object {
        const val DEFAULT_INPUT_SIZE = 640
        const val DEFAULT_MASK_THRESHOLD = 0.5f
        const val DEFAULT_NORM_MEAN = 0.5f
        const val DEFAULT_NORM_STD = 0.5f
        const val CHANNELS_RGB = 3
        const val BYTE_MASK = 0xFF
        const val MASK_FOREGROUND: Byte = 255.toByte()
    }
}
