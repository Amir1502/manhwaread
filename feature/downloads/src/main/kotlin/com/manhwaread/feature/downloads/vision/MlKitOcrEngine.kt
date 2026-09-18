package com.manhwaread.feature.downloads.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.manhwaread.core.vision.DetectedLang
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ML Kit Text Recognition: один распознаватель на язык (ko/ja/latin).
// Сопоставление результатов — чистая функция mapOcrElement (OcrMapper.kt).
class MlKitOcrEngine(
    private val lang: DetectedLang,
    private val recognizer: TextRecognizer,
) : OcrEngine {
    override suspend fun recognize(bitmap: Bitmap, pageIndex: Int): List<OcrLine> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { text -> continuation.resume(mapText(text)) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }

    // Блоки → строки: строка без boundingBox отбрасывается (некуда вписывать).
    private fun mapText(text: Text): List<OcrLine> =
        text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                mapOcrElement(line.text, box.left, box.top, box.right, box.bottom, line.confidence, lang)
            }
        }
}

// Мультязыковой OCR: прогоняет все движки (ko, ja, latin) по одному кадру,
// объединяет, отфильтровывает мусор CJK-движков и дедуплицирует строки по IoU.
// Отказ одного языка не роняет стадию; отказ всех — ошибка VisionFailed.
class MultiLangOcrEngine(private val engines: List<OcrEngine>) : OcrEngine {
    override suspend fun recognize(bitmap: Bitmap, pageIndex: Int): List<OcrLine> {
        val results = engines.map { engine -> runCatching { engine.recognize(bitmap, pageIndex) } }
        val succeeded = results.mapNotNull { result -> result.getOrNull() }
        if (succeeded.isEmpty()) {
            val firstError = results.firstNotNullOfOrNull { result -> result.exceptionOrNull() }
            throw firstError ?: VisionException("no OCR engines configured")
        }
        // Мусор фильтруется ДО дедупликации: высоконадёжная мусорная строка KO/JA
        // иначе вытеснит по IoU корректную латинскую строку в тех же границах.
        return dedupeOcrLines(dropCjkEngineJunk(succeeded.flatten()))
    }
}
