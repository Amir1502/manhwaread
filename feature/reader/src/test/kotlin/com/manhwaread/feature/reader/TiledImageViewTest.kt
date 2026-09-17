package com.manhwaread.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

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

    private companion object {
        const val PNG_512_1024 = "/page512x1024.png"
        const val PNG_512_512 = "/page512x512.png"
        const val VIEW_WIDTH = 400f
        const val VIEW_HEIGHT = 800f
        const val DECODE_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 25L
    }
}
