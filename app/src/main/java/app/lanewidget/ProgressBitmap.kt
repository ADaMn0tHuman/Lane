package app.lanewidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb

/**
 * Полоска прогресса, нарисованная вручную. Заливка считается в пикселях с дробной частью,
 * поэтому двигается плавно. В конце заливки рисуется точка текущей позиции.
 */
object ProgressBitmap {

    private const val W = 720
    private const val H = 16

    fun draw(progress: Float): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = H / 2f

        paint.color = Palette.track.toArgb()
        canvas.drawRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), r, r, paint)

        val fill = W * progress.coerceIn(0f, 1f)
        if (fill >= 1f) {
            paint.color = Palette.pink.toArgb()
            if (fill >= H) {
                canvas.drawRoundRect(RectF(0f, 0f, fill, H.toFloat()), r, r, paint)
            } else {
                // В самом начале показываем только кусочек левой «капсулы».
                canvas.save()
                canvas.clipRect(0f, 0f, fill, H.toFloat())
                canvas.drawRoundRect(RectF(0f, 0f, H.toFloat(), H.toFloat()), r, r, paint)
                canvas.restore()
            }
        }

        // Точка текущей позиции. Не даём ей вылезти за края полоски.
        val dotR = 6f
        paint.color = Palette.text.toArgb()
        canvas.drawCircle(fill.coerceIn(dotR, W - dotR), H / 2f, dotR, paint)
        return bmp
    }
}
