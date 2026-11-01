package app.lanewidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock

/**
 * Плавная смена обложки. Виджет не умеет анимировать картинку сам, поэтому при смене трека
 * мы за 350 мс показываем несколько промежуточных кадров, где старая обложка постепенно
 * проявляется в новую. Кадры запрашивает WidgetUpdater, пока `running` равен true.
 *
 * Важно: обложки сравниваются по ключу (отпечатку картинки), а не по ссылке на объект.
 * Система отдаёт новый объект Bitmap при каждом чтении, и сравнение по ссылке заново
 * запускало бы смену на каждом обновлении, из-за чего картинка зависала на старой.
 */
object CoverFade {

    private const val DURATION_MS = 350L

    private var shown: Bitmap? = null
    private var from: Bitmap? = null
    private var to: Bitmap? = null
    private var toKey: String? = null
    private var startAt = 0L

    @Volatile var running = false
        private set

    fun frame(key: String?, target: Bitmap?): Bitmap? {
        if (target == null || key == null) {
            shown = null
            from = null
            to = null
            toKey = null
            running = false
            return target
        }
        val now = SystemClock.elapsedRealtime()
        if (key != toKey) {
            val prev = shown
            to = target
            toKey = key
            if (prev == null) {
                from = null
                shown = target
                running = false
                return target
            }
            from = prev
            startAt = now
        }
        val f = from
        if (f == null) {
            running = false
            shown = target
            return target
        }
        val t = ((now - startAt).toFloat() / DURATION_MS).coerceIn(0f, 1f)
        if (t >= 1f) {
            running = false
            from = null
            shown = target
            return target
        }
        running = true
        val out = blend(f, target, t)
        shown = out
        return out
    }

    private fun blend(a: Bitmap, b: Bitmap, t: Float): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(a, null, Rect(0, 0, b.width, b.height), paint)
        paint.alpha = (t * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(b, 0f, 0f, paint)
        return out
    }
}
