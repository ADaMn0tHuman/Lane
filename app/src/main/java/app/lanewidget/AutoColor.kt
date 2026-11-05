package app.lanewidget

import android.graphics.Bitmap
import android.graphics.Color as AColor
import kotlin.math.max
import kotlin.math.min

/**
 * Автоподбор цвета кнопок под фон. Смотрим на полосу фона, где стоят кнопки (нижние 40 %).
 * Порядок выбора:
 * 1. цвет из самого фона с достаточным контрастом (берём самые насыщенные);
 * 2. противоположный цвет (инверсия среднего цвета полосы), если он контрастен;
 * 3. чёрный или белый в зависимости от яркости фона.
 */
object AutoColor {

    private const val MIN_CONTRAST = 3.0

    /** img — фон виджета (фото или обложка), null — сплошной цвет fallback. Возвращает ARGB. */
    fun pick(img: Bitmap?, fallback: Int): Int {
        val pixels = if (img == null || img.width < 2 || img.height < 2) listOf(fallback) else samples(img)
        val avg = average(pixels)

        val fromImage = pixels
            .filter { saturation(it) > 0.35f && brightness(it) > 0.35f }
            .sortedByDescending { contrast(it, avg) }
            .firstOrNull { contrast(it, avg) >= MIN_CONTRAST }
        if (fromImage != null) return fromImage

        val inverse = argb(255 - r(avg), 255 - g(avg), 255 - b(avg))
        if (contrast(inverse, avg) >= MIN_CONTRAST) return inverse

        return if (lum(avg) > 0.4) 0xFF1A1A1A.toInt() else 0xFFF2F2F2.toInt()
    }

    private fun samples(img: Bitmap): List<Int> {
        val y0 = (img.height * 0.6f).toInt().coerceIn(0, img.height - 1)
        val strip = Bitmap.createBitmap(img, 0, y0, img.width, img.height - y0)
        val small = Bitmap.createScaledBitmap(strip, 24, 8, true)
        val px = IntArray(24 * 8)
        small.getPixels(px, 0, 24, 0, 0, 24, 8)
        return px.toList()
    }

    private fun average(px: List<Int>): Int {
        var sr = 0L; var sg = 0L; var sb = 0L
        for (p in px) { sr += r(p); sg += g(p); sb += b(p) }
        val n = px.size.coerceAtLeast(1)
        return argb((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt())
    }

    private fun saturation(c: Int): Float {
        val hsv = FloatArray(3)
        AColor.colorToHSV(c, hsv)
        return hsv[1]
    }

    private fun brightness(c: Int): Float {
        val hsv = FloatArray(3)
        AColor.colorToHSV(c, hsv)
        return hsv[2]
    }

    /** Относительная яркость по WCAG. */
    private fun lum(c: Int): Double =
        0.2126 * channel(r(c)) + 0.7152 * channel(g(c)) + 0.0722 * channel(b(c))

    private fun channel(v8: Int): Double {
        val v = v8 / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = lum(a)
        val lb = lum(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
