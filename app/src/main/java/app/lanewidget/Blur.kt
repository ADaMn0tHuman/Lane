package app.lanewidget

import android.graphics.Bitmap

/**
 * Размытие фона. Картинку уменьшаем, несколько раз размываем коробчатым фильтром и растягиваем обратно.
 * Сила 0 — картинка остаётся резкой. Чем сильнее, тем меньше сетка и больше проходов.
 */
object Blur {

    fun soften(src: Bitmap, strength: Int): Bitmap {
        if (strength <= 0 || src.width <= 0 || src.height <= 0) return src
        val s = strength.coerceIn(1, 100)
        val tw = (160 - s * 1.4f).toInt().coerceAtLeast(16)
        val th = (tw.toFloat() * src.height / src.width).toInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(src, tw, th, true)
        val px = IntArray(tw * th)
        small.getPixels(px, 0, tw, 0, 0, tw, th)
        repeat(1 + s / 25) { boxBlur(px, tw, th) }
        val blurred = Bitmap.createBitmap(px, tw, th, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
    }

    /** Коробчатое размытие радиусом 1 по строкам, затем по столбцам. */
    private fun boxBlur(px: IntArray, w: Int, h: Int) {
        val tmp = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (k in -1..1) {
                val p = px[y * w + (x + k).coerceIn(0, w - 1)]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            tmp[y * w + x] = (0xFF shl 24) or ((sr / 3) shl 16) or ((sg / 3) shl 8) or (sb / 3)
        }
        for (y in 0 until h) for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (k in -1..1) {
                val p = tmp[(y + k).coerceIn(0, h - 1) * w + x]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            px[y * w + x] = (0xFF shl 24) or ((sr / 3) shl 16) or ((sg / 3) shl 8) or (sb / 3)
        }
    }
}
