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
import java.util.concurrent.atomic.AtomicBoolean
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
// Этап 3 (надёжность движка):
//  - ключ тайла содержит эпоху изображения — тайл прежней страницы не может
//    попасть на новую, даже если фоновый декод завершился после setImage;
//  - базовый слой: обзорный растр всей страницы (малое разрешение) рисуется
//    под тайлами, поэтому при смене шага прореживания в пинче нет серых дыр;
//  - при отсутствии тайла нужного шага рисуется тайл того же прямоугольника
//    другого шага (fallbackSampleSizes), пока не приземлится точный;
//  - сбойный тайл не перезапрашивается каждый кадр (иначе бесконечный цикл
//    декод → invalidate → декод); OOM сжимает кэш вместо фатальной ошибки;
//  - размеры сетки берутся у самого декодера, а не у вызывающего.
class TiledImageView(context: Context) : View(context) {
    private data class TileKey(val rect: TileRect, val sampleSize: Int, val epoch: Long)

    private class BaseLayer(val epoch: Long, val bitmap: Bitmap)

    private sealed interface DecodeOutcome {
        class Decoded(val bitmap: Bitmap) : DecodeOutcome

        data object Failed : DecodeOutcome

        data object OutOfMemory : DecodeOutcome
    }

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

    // Эпоха изображения: растёт на setImage и detach. Всё, что декодировано
    // под другой эпохой, отбрасывается.
    private val imageEpoch = AtomicLong(0)
    private val baseLayerRequested = AtomicBoolean(false)

    @Volatile
    private var baseLayer: BaseLayer? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = PLACEHOLDER_COLOR }
    private val destRect = RectF()
    private val baseDestRect = RectF()
    private val bandRect = Rect()

    // Защита от livelock: пока летящий батч запрашивает ровно тот же набор
    // недостающих тайлов, поколение не увеличивается и батч не отменяется.
    private var lastRequestedKeys: Set<TileKey> = emptySet()
    private var requestInFlight = false

    // Ключи тайлов, декодирование которых завершилось ошибкой; их не
    // перезапрашиваем. Достижение порога при пустом кэше — жёсткая ошибка
    // страницы (onDecodeError).
    private val failedTileKeys: MutableSet<TileKey> = ConcurrentHashMap.newKeySet()
    private var errorReported = false

    private var decoder: BitmapRegionDecoder? = null
    private var sourceStream: FileInputStream? = null
    private var currentFile: File? = null

    // Размеры, запрошенные вызывающим (для isSameImage), и фактические
    // размеры растра по данным декодера (сетка тайлов и математика).
    private var requestedWidthPx = 0
    private var requestedHeightPx = 0
    private var imageWidthPx = 0
    private var imageHeightPx = 0
    private var transform = ViewportTransform.IDENTITY
    private var baseScale = 1f

    // Файл страницы; размеры известны заранее (от загрузчика) — раскладка
    // возможна без декодирования. Повторная установка того же файла — no-op
    // (в том числе после жёсткой ошибки — до пересоздания вью).
    fun setImage(file: File, widthPx: Int, heightPx: Int) {
        if ((decoder != null || errorReported) && isSameImage(file, widthPx, heightPx)) {
            return
        }
        errorReported = false
        failedTileKeys.clear()
        releaseDecoder()
        tileCache.evictAll()
        baseLayer = null
        baseLayerRequested.set(false)
        imageEpoch.incrementAndGet()
        requestedWidthPx = widthPx
        requestedHeightPx = heightPx
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

    // Тестовые хуки.
    internal fun cachedTileCount(): Int = tileCache.snapshot().size

    internal fun cachedTileEpochs(): Set<Long> = tileCache.snapshot().keys.map { key -> key.epoch }.toSet()

    internal fun currentEpoch(): Long = imageEpoch.get()

    internal fun hasBaseLayer(): Boolean = currentBaseLayer() != null

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
        val base = currentBaseLayer()
        drawBaseLayer(canvas, base)
        val sampleSize = sampleSizeForScale(transform.scale)
        val epoch = imageEpoch.get()
        var missing = false
        for (tile in visibleTiles(visible, imageWidthPx, imageHeightPx)) {
            destRect.set(
                transform.toScreenX(tile.left.toFloat()),
                transform.toScreenY(tile.top.toFloat()),
                transform.toScreenX(tile.right.toFloat()),
                transform.toScreenY(tile.bottom.toFloat()),
            )
            val key = TileKey(tile, sampleSize, epoch)
            val bitmap = tileCache.get(key)
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
                continue
            }
            // Точного тайла ещё нет: тайл другого шага того же прямоугольника,
            // иначе базовый слой, иначе нейтральный серый плейсхолдер.
            val stale = findFallbackTile(tile, sampleSize, epoch)
            if (stale != null) {
                canvas.drawBitmap(stale, null, destRect, bitmapPaint)
            } else if (base == null) {
                canvas.drawRect(destRect, placeholderPaint)
            }
            if (key !in failedTileKeys) {
                missing = true
            }
        }
        if (missing) {
            requestTiles()
        }
    }

    override fun onDetachedFromWindow() {
        generation.incrementAndGet()
        imageEpoch.incrementAndGet()
        requestInFlight = false
        lastRequestedKeys = emptySet()
        decodeExecutor.shutdown()
        releaseDecoder()
        tileCache.evictAll()
        baseLayer = null
        baseLayerRequested.set(false)
        super.onDetachedFromWindow()
    }

    private fun isSameImage(file: File, widthPx: Int, heightPx: Int): Boolean =
        file == currentFile && widthPx == requestedWidthPx && heightPx == requestedHeightPx

    private fun currentBaseLayer(): BaseLayer? = baseLayer?.takeIf { layer -> layer.epoch == imageEpoch.get() }

    private fun drawBaseLayer(canvas: Canvas, base: BaseLayer?) {
        if (base == null) {
            return
        }
        baseDestRect.set(
            transform.toScreenX(0f),
            transform.toScreenY(0f),
            transform.toScreenX(imageWidthPx.toFloat()),
            transform.toScreenY(imageHeightPx.toFloat()),
        )
        canvas.drawBitmap(base.bitmap, null, baseDestRect, bitmapPaint)
    }

    private fun findFallbackTile(tile: TileRect, targetSampleSize: Int, epoch: Long): Bitmap? {
        for (candidate in fallbackSampleSizes(targetSampleSize)) {
            val bitmap = tileCache.get(TileKey(tile, candidate, epoch))
            if (bitmap != null) {
                return bitmap
            }
        }
        return null
    }

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
    // (вью вне экрана, нет изображения или всё уже в кэше / уже сбойное).
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
        val epoch = imageEpoch.get()
        return visibleTiles(visible, imageWidthPx, imageHeightPx)
            .map { tile -> TileKey(tile, sampleSize, epoch) }
            .filter { key -> key !in failedTileKeys && tileCache.get(key) == null }
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
        val epoch = imageEpoch.get()
        // RejectedExecutionException возможен при гонке с detach — не падаем.
        runCatching {
            ensureExecutor().execute { decodeBatch(activeDecoder, missing, currentGeneration, epoch) }
        }.onFailure { requestInFlight = false }
    }

    // Фоновый батч: флаги сбрасываются на главном потоке и только если
    // поколение ещё наше — иначе эстафету несёт более новый батч, а прежде-
    // временный сброс снова разрешил бы отмену летящего декодирования.
    // Базовый слой строится после первого батча страницы: видимые тайлы
    // приоритетнее обзорного растра.
    private fun decodeBatch(
        activeDecoder: BitmapRegionDecoder,
        keys: Set<TileKey>,
        batchGeneration: Long,
        epoch: Long,
    ) {
        for (key in keys) {
            if (generation.get() != batchGeneration) {
                break
            }
            when (val outcome = decodeTile(activeDecoder, key)) {
                is DecodeOutcome.Decoded -> {
                    // Тайл прежнего изображения не кладём в кэш нового.
                    if (imageEpoch.get() == epoch) {
                        tileCache.put(key, outcome.bitmap)
                    }
                }
                DecodeOutcome.Failed -> recordTileFailure(key)
                DecodeOutcome.OutOfMemory -> handleOutOfMemory(key)
            }
        }
        decodeBaseLayerIfNeeded(activeDecoder, epoch)
        mainHandler.post {
            if (generation.get() == batchGeneration) {
                requestInFlight = false
            }
            invalidate()
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

    private fun decodeTile(activeDecoder: BitmapRegionDecoder, key: TileKey): DecodeOutcome {
        val region = Rect(key.rect.left, key.rect.top, key.rect.right, key.rect.bottom)
        return decodeRegionLocked(activeDecoder, region, key.sampleSize)
    }

    private fun decodeBaseLayerIfNeeded(activeDecoder: BitmapRegionDecoder, epoch: Long) {
        if (!baseLayerRequested.compareAndSet(false, true)) {
            return
        }
        val region = Rect(0, 0, imageWidthPx, imageHeightPx)
        val sample = baseLayerSampleSize(imageWidthPx, imageHeightPx)
        val outcome = decodeRegionLocked(activeDecoder, region, sample)
        if (outcome is DecodeOutcome.Decoded && imageEpoch.get() == epoch) {
            baseLayer = BaseLayer(epoch, outcome.bitmap)
        }
    }

    // Декод под замком: recycle/close декодера не может случиться внутри
    // decodeRegion. Ошибки не молчаливы: результат различает сбой и OOM.
    private fun decodeRegionLocked(
        activeDecoder: BitmapRegionDecoder,
        region: Rect,
        sampleSize: Int,
    ): DecodeOutcome = synchronized(decoderLock) {
        if (activeDecoder.isRecycled) {
            return@synchronized DecodeOutcome.Failed
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val result = runCatching { activeDecoder.decodeRegion(region, options) }
        val bitmap = result.getOrNull()
        when {
            bitmap != null -> DecodeOutcome.Decoded(bitmap)
            result.exceptionOrNull() is OutOfMemoryError -> DecodeOutcome.OutOfMemory
            else -> DecodeOutcome.Failed
        }
    }

    // Порог зависит от числа тайлов самой страницы: порог 3 при одном тайле
    // не наступил бы никогда. Сбойные ключи не перезапрашиваются.
    private fun recordTileFailure(key: TileKey) {
        failedTileKeys += key
        val limit = tileFailureLimit(imageWidthPx, imageHeightPx)
        if (failedTileKeys.size >= limit && tileCache.size() == 0) {
            reportDecodeError()
        }
    }

    // OOM при непустом кэше — освобождаем половину и пробуем позже (тайл не
    // помечается сбойным); при пустом кэше памяти не хватит и на один тайл —
    // это обычная ошибка страницы.
    private fun handleOutOfMemory(key: TileKey) {
        if (tileCache.size() == 0) {
            recordTileFailure(key)
        } else {
            tileCache.trimToSize(tileCache.size() / 2)
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
        adoptDecoder(created, stream = null)
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
        adoptDecoder(created, stream)
    }

    // Декодер с нулевыми размерами непригоден; сетка тайлов строится по
    // фактическим размерам растра, чтобы запрос региона за пределами
    // изображения не превращался в сбой каждого тайла.
    private fun adoptDecoder(created: BitmapRegionDecoder, stream: FileInputStream?) {
        val actualWidth = created.width
        val actualHeight = created.height
        if (actualWidth <= 0 || actualHeight <= 0) {
            runCatching { created.recycle() }
            runCatching { stream?.close() }
            reportDecodeError()
            return
        }
        synchronized(decoderLock) {
            decoder = created
            sourceStream = stream
        }
        imageWidthPx = actualWidth
        imageHeightPx = actualHeight
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

        // Нейтральный серый плейсхолдер незагруженного тайла (совпадает с UI-заглушкой).
        val PLACEHOLDER_COLOR: Int = 0xFF2C2F36.toInt()

        fun cacheBudgetBytes(): Int = (Runtime.getRuntime().maxMemory() / CACHE_MEMORY_DIVISOR).toInt()
    }
}
