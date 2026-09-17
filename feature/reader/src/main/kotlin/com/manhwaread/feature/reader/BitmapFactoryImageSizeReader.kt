package com.manhwaread.feature.reader

import android.graphics.BitmapFactory
import java.io.File

// Читатель размеров через BitmapFactory: декодируются только заголовки
// (inJustDecodeBounds), растр в память не попадает.
class BitmapFactoryImageSizeReader : ImageSizeReader {
    override fun read(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "unreadable image: ${file.name}" }
        return options.outWidth to options.outHeight
    }
}
