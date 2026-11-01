package app.lanewidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb

/**
 * Полоска прогресса, нарисованная вручную. Холст выше самой полоски, чтобы точка
 * текущей позиции не обрезалась. Полоска занимает средние 16 px из 32.
 */
object ProgressBitmap {

    private const val W = 720
    private const val H = 32
    private const val BAR_TOP = 8f
    private const val BAR_BOTTOM = 24f
    private const val BAR_H = 16f
    private const val DOT_R = 12f

    fun draw(progress: Float): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = BAR_H / 2f

        paint.color = Palette.track.toArgb()
        canvas.drawRoundRect(RectF(0f, BAR_TOP, W.toFloat(), BAR_BOTTOM), r, r, paint)

        val fill = W * progress.coerceIn(0f, 1f)
        if (fill >= 1f) {
            paint.color = Palette.pink.toArgb()
            if (fill >= BAR_H) {
                canvas.drawRoundRect(RectF(0f, BAR_TOP, fill, BAR_BOTTOM), r, r, paint)
            } else {
                // В самом начале показываем только кусочек левой «капсулы».
                canvas.save()
                canvas.clipRect(0f, 0f, fill, H.toFloat())
                canvas.drawRoundRect(RectF(0f, BAR_TOP, BAR_H, BAR_BOTTOM), r, r, paint)
                canvas.restore()
            }
        }

        // Точка текущей позиции. Не даём ей выйти за края холста.
        paint.color = Palette.dot.toArgb()
        canvas.drawCircle(fill.coerceIn(DOT_R, W - DOT_R), H / 2f, DOT_R, paint)
        return bmp
    }
}
