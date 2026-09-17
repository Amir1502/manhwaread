package com.manhwaread.core.typeset

// Маска хранится для отдельного облачка, а не для всей длинной страницы.
class PixelMask(val width: Int, val height: Int, pixels: BooleanArray) {
    private val pixels: BooleanArray

    init {
        require(width > 0 && height > 0)
        require(width.toLong() * height <= MAX_PIXELS)
        require(pixels.size.toLong() == width.toLong() * height)
        this.pixels = pixels.copyOf()
    }

    operator fun get(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && pixels[y * width + x]

    fun containsInk(ink: PixelMask, offsetX: Int, offsetY: Int): Boolean {
        for (y in 0 until ink.height) {
            for (x in 0 until ink.width) {
                if (!ink[x, y]) continue
                val targetX = x.toLong() + offsetX
                val targetY = y.toLong() + offsetY
                if (targetX !in 0L until width.toLong() || targetY !in 0L until height.toLong()) return false
                if (!get(targetX.toInt(), targetY.toInt())) return false
            }
        }
        return true
    }

    // Квадратная эрозия через интегральное изображение: время не зависит от радиуса.
    fun erode(radius: Int): PixelMask {
        require(radius >= 0)
        if (radius == 0) return PixelMask(width, height, pixels)
        val output = BooleanArray(pixels.size)
        val diameter = radius.toLong() * 2 + 1
        if (diameter > width || diameter > height) return PixelMask(width, height, output)
        val stride = width + 1
        val integral = IntArray(stride * (height + 1))
        for (y in 0 until height) {
            var row = 0
            for (x in 0 until width) {
                if (get(x, y)) row++
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
            }
        }
        val area = (diameter * diameter).toInt()
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                val left = x - radius
                val top = y - radius
                val right = x + radius + 1
                val bottom = y + radius + 1
                val sum = integral[bottom * stride + right] - integral[top * stride + right] -
                    integral[bottom * stride + left] + integral[top * stride + left]
                output[y * width + x] = sum == area
            }
        }
        return PixelMask(width, height, output)
    }

    companion object {
        const val MAX_PIXELS = 4_194_304L
    }
}
