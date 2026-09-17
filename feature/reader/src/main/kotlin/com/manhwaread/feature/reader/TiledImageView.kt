package com.manhwaread.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

// Тайловый рендеринг страницы: BitmapRegionDecoder декодирует только видимые
// участки изображения, поэтому страница вебтуна 800×20000 не приводит к OOM
// (DoD). Декодирование — фоновый поток, кэш — LRU с бюджетом 1/8 heap,
// отмену устаревших запросов обеспечивает счётчик поколений.
class TiledImageView(context: Context) : View(context) {
    private data class TileKey(val rect: TileRect, val sampleSize: Int)

    private val decoderLock = Any()
    private val tileCache = object : LruCache<TileKey, Bitmap>(cacheBudgetBytes()) {
        override fun sizeOf(key: TileKey, value: Bitmap): Int = value.byteCount
    }
    private val decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destRect = RectF()

    private var decoder: BitmapRegionDecoder? = null
    private var sourceStream: FileInputStream? = null
    private var currentFile: File? = null
    private var imageWidthPx = 0
    private var imageHeightPx = 0
    private var transform = ViewportTransform.IDENTITY
    private var baseScale = 1f

    // Файл страницы; размеры известны заранее (от загрузчика) — раскладка
    // возможна без декодирования. Повторная установка того же файла — no-op.
    fun setImage(file: File, widthPx: Int, heightPx: Int) {
        if (decoder != null && isSameImage(file, widthPx, heightPx)) {
            return
        }
        releaseDecoder()
        tileCache.evictAll()
        imageWidthPx = widthPx
        imageHeightPx = heightPx
        currentFile = file
        openDecoder(file)
        requestTiles()
        invalidate()
    }

    fun setViewport(newTransform: ViewportTransform, newBaseScale: Float) {
        if (transform == newTransform && baseScale == newBaseScale) {
            return
        }
        transform = newTransform
        baseScale = newBaseScale
        requestTiles()
        invalidate()
    }

    // Тестовый хук: число декодированных тайлов в кэше.
    internal fun cachedTileCount(): Int = tileCache.size()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidthPx == 0 || imageHeightPx == 0) {
            return
        }
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val visible = visibleImageRect(transform, viewWidth, viewHeight, imageWidthPx, imageHeightPx) ?: return
        val sampleSize = sampleSizeFor(transform.scale / baseScale)
        var missing = false
        for (tile in visibleTiles(visible, imageWidthPx, imageHeightPx)) {
            val bitmap = tileCache.get(TileKey(tile, sampleSize))
            if (bitmap == null) {
                missing = true
                continue
            }
            destRect.set(
                transform.toScreenX(tile.left.toFloat()),
                transform.toScreenY(tile.top.toFloat()),
                transform.toScreenX(tile.right.toFloat()),
                transform.toScreenY(tile.bottom.toFloat()),
            )
            canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
        }
        if (missing) {
            requestTiles()
        }
    }

    override fun onDetachedFromWindow() {
        generation.incrementAndGet()
        decodeExecutor.shutdown()
        releaseDecoder()
        tileCache.evictAll()
        super.onDetachedFromWindow()
    }

    private fun isSameImage(file: File, widthPx: Int, heightPx: Int): Boolean =
        file == currentFile && widthPx == imageWidthPx && heightPx == imageHeightPx

    // Ставит в фон декод недостающих видимых тайлов; смена поколения
    // отменяет устаревшие запросы, invalidate — по готовности.
    private fun requestTiles() {
        val activeDecoder = decoder ?: return
        if (width == 0 || height == 0 || imageWidthPx == 0) {
            return
        }
        val visible = visibleImageRect(transform, width.toFloat(), height.toFloat(), imageWidthPx, imageHeightPx)
            ?: return
        val sampleSize = sampleSizeFor(transform.scale / baseScale)
        val missing = visibleTiles(visible, imageWidthPx, imageHeightPx)
            .map { tile -> TileKey(tile, sampleSize) }
            .filter { key -> tileCache.get(key) == null }
        if (missing.isEmpty()) {
            return
        }
        val currentGeneration = generation.incrementAndGet()
        decodeExecutor.execute {
            for (key in missing) {
                if (generation.get() != currentGeneration) {
                    return@execute
                }
                decodeTile(activeDecoder, key)?.let { bitmap -> tileCache.put(key, bitmap) }
            }
            if (generation.get() == currentGeneration) {
                mainHandler.post { invalidate() }
            }
        }
    }

    private fun decodeTile(activeDecoder: BitmapRegionDecoder, key: TileKey): Bitmap? =
        synchronized(decoderLock) {
            if (activeDecoder.isRecycled) {
                null
            } else {
                val options = BitmapFactory.Options().apply { inSampleSize = key.sampleSize }
                val region = Rect(key.rect.left, key.rect.top, key.rect.right, key.rect.bottom)
                runCatching { activeDecoder.decodeRegion(region, options) }.getOrNull()
            }
        }

    private fun openDecoder(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openDecoderFromFile(file)
        } else {
            openDecoderFromStream(file)
        }
    }

    // API 31+: path-перегрузка newInstance(String) — без управления потоком.
    // Вызов защищё проверкой SDK_INT в openDecoder; аннотация делает
    // защиту видимой для lint через границы функций.
    @RequiresApi(Build.VERSION_CODES.S)
    private fun openDecoderFromFile(file: File) {
        val created = runCatching { BitmapRegionDecoder.newInstance(file.absolutePath) }.getOrNull() ?: return
        synchronized(decoderLock) {
            decoder = created
        }
    }

    // Двухпараметрический newInstance(InputStream, isShareable) существует с
    // API 10; однопараметрические перегрузки появились только в API 31.
    // Поток остаётся открытым: декодер читает из него весь срок жизни.
    private fun openDecoderFromStream(file: File) {
        val stream = runCatching { FileInputStream(file) }.getOrNull() ?: return
        val created = runCatching { BitmapRegionDecoder.newInstance(stream, false) }.getOrNull()
        if (created == null) {
            runCatching { stream.close() }
            return
        }
        synchronized(decoderLock) {
            decoder = created
            sourceStream = stream
        }
    }

    // Закрытие — под замком: поток декодирования не может находиться внутри
    // decodeRegion одновременно с recycle/close.
    private fun releaseDecoder() {
        generation.incrementAndGet()
        synchronized(decoderLock) {
            decoder?.takeIf { active -> !active.isRecycled }?.recycle()
            decoder = null
            runCatching { sourceStream?.close() }
            sourceStream = null
        }
        currentFile = null
    }

    private companion object {
        // Бюджет LRU-кэша: 1/8 heap — стандартная практика кэшей изображений.
        const val CACHE_MEMORY_DIVISOR = 8L

        fun cacheBudgetBytes(): Int = (Runtime.getRuntime().maxMemory() / CACHE_MEMORY_DIVISOR).toInt()
    }
}
