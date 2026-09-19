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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

// Тайловый рендеринг страницы: BitmapRegionDecoder декодирует только видимые
// участки изображения, поэтому страница вебтуна 800×20000 не приводит к OOM
// (DoD). Декодирование — фоновый поток, кэш — LRU с бюджетом 1/8 heap,
// отмену устаревших запросов обеспечивает счётчик поколений.
// Анти-флеш гарантии: недостроенные тайлы рисуются нейтральным серым
// плейсхолдером (не чёрными дырами), а повторяющийся запрос того же набора
// тайлов НЕ отменяет летящее декодирование (иначе при скролле/пинче батчи
// прерывались бы каждый кадр и тайлы не успевали приземлиться — livelock).
// Тайловая математика обрезана по фактически видимой полосе вью
// (getLocalVisibleRect), поэтому длинная страница вебтуна не тянет в кэш
// все тайлы сразу.
class TiledImageView(context: Context) : View(context) {
    private data class TileKey(val rect: TileRect, val sampleSize: Int)

    // Колбэк жёсткой ошибки декодирования (декодер не открылся или тайлы
    // стабильно не декодируются). Вызывается на главном потоке, один раз
    // на изображение — до явного setImage другого/того же файла.
    var onDecodeError: (() -> Unit)? = null

    private val decoderLock = Any()
    private val tileCache = object : LruCache<TileKey, Bitmap>(cacheBudgetBytes()) {
        override fun sizeOf(key: TileKey, value: Bitmap): Int = value.byteCount
    }
    private var decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = PLACEHOLDER_COLOR }
    private val destRect = RectF()
    private val bandRect = Rect()

    // Защита от livelock: пока летящий батч запрашивает ровно тот же набор
    // недостающих тайлов, поколение не увеличивается и батч не отменяется.
    private var lastRequestedKeys: Set<TileKey> = emptySet()
    private var requestInFlight = false

    // Ключи тайлов, декодирование которых завершилось ошибкой; >= 3 разных
    // ключей при пустом кэше — жёсткая ошибка страницы (onDecodeError).
    private val failedTileKeys: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()
    private var errorReported = false

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
        errorReported = false
        failedTileKeys.clear()
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
        // Полностью вне экрана — не рисуем и не запрашиваем декодирование.
        val band = visibleBand() ?: return
        val visible = visibleImageRectForBand(
            transform = transform,
            bandLeftPx = band.left.toFloat(),
            bandTopPx = band.top.toFloat(),
            bandRightPx = band.right.toFloat(),
            bandBottomPx = band.bottom.toFloat(),
            imageWidthPx = imageWidthPx,
            imageHeightPx = imageHeightPx,
        ) ?: return
        val sampleSize = sampleSizeForScale(transform.scale)
        var missing = false
        for (tile in visibleTiles(visible, imageWidthPx, imageHeightPx)) {
            destRect.set(
                transform.toScreenX(tile.left.toFloat()),
                transform.toScreenY(tile.top.toFloat()),
                transform.toScreenX(tile.right.toFloat()),
                transform.toScreenY(tile.bottom.toFloat()),
            )
            val bitmap = tileCache.get(TileKey(tile, sampleSize))
            if (bitmap == null) {
                // Плейсхолдер вместо пропуска: пользователь видит нейтральные
                // серые квадраты загрузки, а не чёрные дыры фона темы.
                missing = true
                canvas.drawRect(destRect, placeholderPaint)
            } else {
                canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
            }
        }
        if (missing) {
            requestTiles()
        }
    }

    override fun onDetachedFromWindow() {
        generation.incrementAndGet()
        requestInFlight = false
        lastRequestedKeys = emptySet()
        decodeExecutor.shutdown()
        releaseDecoder()
        tileCache.evictAll()
        super.onDetachedFromWindow()
    }

    private fun isSameImage(file: File, widthPx: Int, heightPx: Int): Boolean =
        file == currentFile && widthPx == imageWidthPx && heightPx == imageHeightPx

    // Фактически видимая полоса вью в её собственных координатах: учитывает
    // обрезку родительскими контейнерами (LazyColumn). Null — вью не
    // отрисовывается (нулевой размер или полностью за пределами экрана).
    private fun visibleBand(): Rect? {
        if (width == 0 || height == 0) {
            return null
        }
        bandRect.setEmpty()
        if (!getLocalVisibleRect(bandRect)) {
            return null
        }
        if (!bandRect.intersect(0, 0, width, height)) {
            return null
        }
        return bandRect
    }

    // Недостающие тайлы видимой полосы; пустой набор — запрашивать нечего
    // (вью вне экрана, нет изображения или всё уже в кэше).
    private fun missingTileKeys(): Set<TileKey> {
        if (width == 0 || height == 0 || imageWidthPx == 0) {
            return emptySet()
        }
        val band = visibleBand() ?: return emptySet()
        val visible = visibleImageRectForBand(
            transform = transform,
            bandLeftPx = band.left.toFloat(),
            bandTopPx = band.top.toFloat(),
            bandRightPx = band.right.toFloat(),
            bandBottomPx = band.bottom.toFloat(),
            imageWidthPx = imageWidthPx,
            imageHeightPx = imageHeightPx,
        ) ?: return emptySet()
        val sampleSize = sampleSizeForScale(transform.scale)
        return visibleTiles(visible, imageWidthPx, imageHeightPx)
            .map { tile -> TileKey(tile, sampleSize) }
            .filter { key -> tileCache.get(key) == null }
            .toSet()
    }

    // Ставит в фон декод недостающих видимых тайлов. Поколение увеличивается
    // только когда набор реально изменился: повторный запрос того же набора
    // при летящем батче не отменяет его (устранение livelock при скролле).
    private fun requestTiles() {
        val activeDecoder = decoder ?: return
        val missing = missingTileKeys()
        if (missing.isEmpty()) {
            return
        }
        if (requestInFlight && missing == lastRequestedKeys) {
            return
        }
        lastRequestedKeys = missing
        requestInFlight = true
        val currentGeneration = generation.incrementAndGet()
        // RejectedExecutionException возможен при гонке с detach — не падаем.
        runCatching { ensureExecutor().execute { decodeBatch(activeDecoder, missing, currentGeneration) } }
            .onFailure { requestInFlight = false }
    }

    // Фоновый батч: флаги сбрасываются на главном потоке и только если
    // поколение ещё наше — иначе эстафету несёт более новый батч, а прежде-
    // временный сброс снова разрешил бы отмену летящего декодирования.
    private fun decodeBatch(activeDecoder: BitmapRegionDecoder, keys: Set<TileKey>, batchGeneration: Long) {
        for (key in keys) {
            if (generation.get() != batchGeneration) {
                break
            }
            val bitmap = decodeTile(activeDecoder, key)
            if (bitmap == null) {
                recordTileFailure(key)
            } else {
                tileCache.put(key, bitmap)
            }
        }
        mainHandler.post {
            if (generation.get() == batchGeneration) {
                requestInFlight = false
                invalidate()
            }
        }
    }

    // Исполнитель пересоздаётся после shutdown (LazyColumn может повторно
    // приаттачить тот же экземпляр вью после onDetachedFromWindow).
    private fun ensureExecutor(): ExecutorService {
        if (decodeExecutor.isShutdown) {
            decodeExecutor = Executors.newSingleThreadExecutor()
        }
        return decodeExecutor
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

    // Ошибка тайла не молчалива: >= 3 разных сбойных тайла при пустом кэше —
    // страница недекодируема, сообщаем наружу (UI покажет ошибку и retry).
    private fun recordTileFailure(key: TileKey) {
        failedTileKeys += key
        if (failedTileKeys.size >= TILE_FAILURE_LIMIT && tileCache.size() == 0) {
            reportDecodeError()
        }
    }

    private fun reportDecodeError() {
        if (errorReported) {
            return
        }
        errorReported = true
        mainHandler.post { onDecodeError?.invoke() }
    }

    private fun openDecoder(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openDecoderFromFile(file)
        } else {
            openDecoderFromStream(file)
        }
    }

    // API 31+: path-перегрузка newInstance(String) — без управления потоком.
    // Вызов защищён проверкой SDK_INT в openDecoder; аннотация делает
    // защиту видимой для lint через границы функций.
    @RequiresApi(Build.VERSION_CODES.S)
    private fun openDecoderFromFile(file: File) {
        val created = runCatching { BitmapRegionDecoder.newInstance(file.absolutePath) }.getOrNull()
        if (created == null) {
            reportDecodeError()
            return
        }
        synchronized(decoderLock) {
            decoder = created
        }
    }

    // Двухпараметрический newInstance(InputStream, isShareable) существует с
    // API 10; однопараметрические перегрузки появились только в API 31.
    // Поток остаётся открытым: декодер читает из него весь срок жизни.
    private fun openDecoderFromStream(file: File) {
        val stream = runCatching { FileInputStream(file) }.getOrNull()
        if (stream == null) {
            reportDecodeError()
            return
        }
        val created = runCatching { BitmapRegionDecoder.newInstance(stream, false) }.getOrNull()
        if (created == null) {
            runCatching { stream.close() }
            reportDecodeError()
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
        requestInFlight = false
        lastRequestedKeys = emptySet()
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

        // Число разных сбойных тайлов, после которого страница считается недекодируемой.
        const val TILE_FAILURE_LIMIT = 3

        // Нейтральный серый плейсхолдер незагруженного тайла (совпадает с UI-заглушкой).
        val PLACEHOLDER_COLOR: Int = 0xFF2C2F36.toInt()

        fun cacheBudgetBytes(): Int = (Runtime.getRuntime().maxMemory() / CACHE_MEMORY_DIVISOR).toInt()
    }
}
