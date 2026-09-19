package com.manhwaread.feature.downloads.queue

import android.graphics.BitmapFactory

// Проверка целостности растра (фикс чёрных страниц скачанных глав). Два рубежа:
// 1) магические байты PNG/JPEG/WebP — антибот-HTML с кодом 200 отсекается сразу
//    (согласовано с выбором расширения в FilePageStore: прочие форматы пишутся
//    .bin, который читалка игнорирует, — это и есть тихая порча главы);
// 2) декодирование границ (inJustDecodeBounds) — ловит усечённые файлы с целым
//    заголовком после смерти процесса.
// Растр в памяти не выделяется: проверка безопасна для длинных вебтун-страниц.
internal fun isDecodableImage(bytes: ByteArray): Boolean =
    hasImageMagic(bytes) && decodeBoundsOk(bytes)

private fun decodeBoundsOk(bytes: ByteArray): Boolean = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    options.outWidth > 0 && options.outHeight > 0
}.getOrDefault(false)

private fun hasImageMagic(bytes: ByteArray): Boolean =
    startsWith(bytes, PNG_MAGIC) || startsWith(bytes, JPEG_MAGIC) || isWebp(bytes)

private fun isWebp(bytes: ByteArray): Boolean =
    bytes.size > WEBP_HEADER_END &&
        startsWith(bytes, RIFF_MAGIC) &&
        String(bytes, WEBP_MARKER_OFFSET, WEBP_MARKER_LENGTH, Charsets.US_ASCII) == WEBP_MARKER

private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean =
    bytes.size >= prefix.size && prefix.indices.all { index -> bytes[index] == prefix[index] }

private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val RIFF_MAGIC = "RIFF".toByteArray(Charsets.US_ASCII)
private const val WEBP_MARKER = "WEBP"
private const val WEBP_MARKER_OFFSET = 8
private const val WEBP_MARKER_LENGTH = 4
private const val WEBP_HEADER_END = 12
