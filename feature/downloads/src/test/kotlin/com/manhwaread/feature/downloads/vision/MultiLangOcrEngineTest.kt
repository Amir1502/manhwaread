package com.manhwaread.feature.downloads.vision

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.RectF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Прод-путь мультязыкового OCR на фейковых движках: фильтр CJK-мусора
// применяется до дедупликации (Bitmap — настоящий, Robolectric).
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MultiLangOcrEngineTest {
    private class FakeEngine(
        private val lines: List<OcrLine>,
        private val failure: Throwable? = null,
    ) : OcrEngine {
        override suspend fun recognize(bitmap: Bitmap, pageIndex: Int): List<OcrLine> {
            if (failure != null) {
                throw failure
            }
            return lines
        }
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(BITMAP_SIZE_PX, BITMAP_SIZE_PX, Bitmap.Config.ARGB_8888)

    private fun line(text: String, lang: DetectedLang, confidence: Float) =
        OcrLine(text = text, bounds = RectF(0f, 0f, 100f, 20f), confidence = confidence, lang = lang)

    @Test
    fun `junk KO line does not crowd out latin line in same bounds`() = runBlocking {
        // Мусорная KO-строка надёжнее латинской; без фильтра до дедупликации
        // она вытеснила бы корректную EN-строку, а затем сама была бы сброшена.
        val koJunk = line("TODAVÍA", DetectedLang.KO, confidence = 0.99f)
        val enGood = line("TODAVÍA", DetectedLang.EN, confidence = 0.6f)
        val engine = MultiLangOcrEngine(listOf(FakeEngine(listOf(koJunk)), FakeEngine(listOf(enGood))))

        val result = engine.recognize(bitmap(), 0)

        assertEquals(listOf(enGood), result)
    }

    @Test
    fun `hangul KO line is kept and failing engine is tolerated`() = runBlocking {
        val hangul = line("안녕", DetectedLang.KO, confidence = 0.9f)
        val engine = MultiLangOcrEngine(
            listOf(
                FakeEngine(listOf(hangul)),
                FakeEngine(emptyList(), VisionException("engine down")),
            ),
        )

        assertEquals(listOf(hangul), engine.recognize(bitmap(), 0))
    }

    @Test
    fun `all engines failing rethrows first error`() = runBlocking {
        val engine = MultiLangOcrEngine(listOf(FakeEngine(emptyList(), VisionException("ko down"))))

        val outcome = runCatching { engine.recognize(bitmap(), 0) }

        assertTrue(outcome.exceptionOrNull() is VisionException)
        assertEquals("ko down", outcome.exceptionOrNull()?.message)
    }

    private companion object {
        const val BITMAP_SIZE_PX = 4
    }
}
