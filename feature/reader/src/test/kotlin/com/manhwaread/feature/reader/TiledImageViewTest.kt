package com.manhwaread.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

// Рендер-путь TiledImageView на JVM: настоящие PNG из тестовых ресурсов,
// тайловое декодирование и отрисовка без падений; кэш заполняется тайлами.
// sdk=30 — shadow Robolectric покрывает только классическую перегрузку
// newInstance(InputStream); ветка API 31+ исполняется платформой и для JVM
// недоступна (её логика разбита в TileMathTest чистыми функциями).
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class TiledImageViewTest {
    private fun writePngFromResources(resource: String, file: File) {
        val bytes = javaClass.getResourceAsStream(resource)?.use { input -> input.readBytes() }
            ?: error("missing test resource: $resource")
        file.writeBytes(bytes)
    }

    private fun measureLayoutAndDraw(view: TiledImageView, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val canvas = Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        view.draw(canvas)
    }

    // Декодирование идёт в реальном фоновом потоке: ждём заполнения кэша
    // реальным временем, попутно прокручивая главный looper.
    private fun awaitTiles(view: TiledImageView): Int {
        val deadline = System.currentTimeMillis() + DECODE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && view.cachedTileCount() == 0) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return view.cachedTileCount()
    }

    @Test
    fun `setImage then draw decodes visible tiles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "page.png")
        writePngFromResources(PNG_512_1024, file)
        val view = TiledImageView(context)
        view.setImage(file, widthPx = 512, heightPx = 1024)
        view.setViewport(fitToWidth(512, VIEW_WIDTH), baseScaleFor(512, VIEW_WIDTH))
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue("tiles must be decoded into cache", awaitTiles(view) > 0)
        // После заполнения кэша повторная отрисовка стабильна.
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
    }

    @Test
    fun `repeated setImage with same file keeps view drawable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "page2.png")
        writePngFromResources(PNG_512_512, file)
        val view = TiledImageView(context)
        view.setImage(file, widthPx = 512, heightPx = 512)
        view.setImage(file, widthPx = 512, heightPx = 512)
        view.setViewport(fitToWidth(512, VIEW_WIDTH), baseScaleFor(512, VIEW_WIDTH))
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue(awaitTiles(view) > 0)
    }

    @Test
    fun `draw without image does not crash`() {
        val view = TiledImageView(ApplicationProvider.getApplicationContext<Context>())
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue(view.cachedTileCount() == 0)
    }

    @Test
    fun `stress zoom and pan never crashes and tiles still land`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "stress.png")
        writePngFromResources(PNG_512_1024, file)
        val view = TiledImageView(context)
        view.setImage(file, widthPx = 512, heightPx = 1024)
        val base = baseScaleFor(512, VIEW_WIDTH)
        repeat(STRESS_ITERATIONS) { step ->
            // Масштаб гуляет от базового до 5x и обратно, сдвиг — псевдослучайный.
            val phase = (step % ZOOM_CYCLE).toFloat() / ZOOM_CYCLE
            val scale = base * (1f + (ViewportTransform.MAX_ZOOM - 1f) * phase)
            val offsetX = -((step * OFFSET_STEP) % 300).toFloat()
            val offsetY = -((step * OFFSET_STEP) % 600).toFloat()
            view.setViewport(ViewportTransform(scale, offsetX, offsetY), base)
            measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
            if (step % IDLE_EVERY == 0) {
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
        view.setViewport(fitToWidth(512, VIEW_WIDTH), base)
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue("tiles must land after the stress", awaitTiles(view) > 0)
    }

    @Test
    fun `base layer is built after first batch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "base.png")
        writePngFromResources(PNG_512_1024, file)
        val view = TiledImageView(context)
        view.setImage(file, widthPx = 512, heightPx = 1024)
        view.setViewport(fitToWidth(512, VIEW_WIDTH), baseScaleFor(512, VIEW_WIDTH))
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue(awaitTiles(view) > 0)
        assertTrue("base layer must appear", awaitCondition { view.hasBaseLayer() })
    }

    @Test
    fun `garbage file reports decode error once and does not crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "garbage.png")
        file.writeBytes(ByteArray(GARBAGE_SIZE) { index -> (index * 31).toByte() })
        val errors = AtomicInteger(0)
        val view = TiledImageView(context).apply { onDecodeError = { errors.incrementAndGet() } }
        view.setImage(file, widthPx = 512, heightPx = 512)
        view.setViewport(fitToWidth(512, VIEW_WIDTH), baseScaleFor(512, VIEW_WIDTH))
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue("decode error must be reported", awaitCondition { errors.get() > 0 })
        // Повторные setImage того же файла и перерисовки не дублируют ошибку.
        view.setImage(file, widthPx = 512, heightPx = 512)
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, errors.get())
        assertEquals(0, view.cachedTileCount())
    }

    @Test
    fun `missing file reports decode error`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val errors = AtomicInteger(0)
        val view = TiledImageView(context).apply { onDecodeError = { errors.incrementAndGet() } }
        view.setImage(File(context.cacheDir, "does-not-exist.png"), widthPx = 512, heightPx = 512)
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue("decode error must be reported", awaitCondition { errors.get() > 0 })
    }

    @Test
    fun `swapping image never leaves tiles of the previous epoch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = File(context.cacheDir, "swap-a.png")
        val second = File(context.cacheDir, "swap-b.png")
        writePngFromResources(PNG_512_1024, first)
        writePngFromResources(PNG_512_512, second)
        val view = TiledImageView(context)
        view.setViewport(fitToWidth(512, VIEW_WIDTH), baseScaleFor(512, VIEW_WIDTH))
        view.setImage(first, widthPx = 512, heightPx = 1024)
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        // Меняем страницу, пока фоновый декод первой может быть в полёте.
        view.setImage(second, widthPx = 512, heightPx = 512)
        measureLayoutAndDraw(view, VIEW_WIDTH.toInt(), VIEW_HEIGHT.toInt())
        assertTrue(awaitTiles(view) > 0)
        Thread.sleep(SETTLE_MS)
        shadowOf(Looper.getMainLooper()).idle()
        val epochs = view.cachedTileEpochs()
        assertTrue("stale epochs in cache: $epochs", epochs.all { epoch -> epoch == view.currentEpoch() })
    }

    // Ожидание произвольного условия с прокруткой главного looper.
    private fun awaitCondition(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + DECODE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && !condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    private companion object {
        const val PNG_512_1024 = "/page512x1024.png"
        const val PNG_512_512 = "/page512x512.png"
        const val VIEW_WIDTH = 400f
        const val VIEW_HEIGHT = 800f
        const val DECODE_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 25L
        const val STRESS_ITERATIONS = 150
        const val ZOOM_CYCLE = 20
        const val OFFSET_STEP = 37
        const val IDLE_EVERY = 5
        const val GARBAGE_SIZE = 256
        const val SETTLE_MS = 200L
    }
}
