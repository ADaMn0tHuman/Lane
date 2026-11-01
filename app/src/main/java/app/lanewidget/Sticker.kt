package app.lanewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.File

/**
 * Наклейка на большом виджете. Картинка с прозрачным фоном (PNG или WebP) копируется во внутреннюю
 * память приложения. Размер и поворот применяются к слою того же размера, что виджет.
 * Всё, что выходит за края, обрезается.
 */
object Sticker {

    private const val PREFS = "lane_widget"
    private const val KEY_ON = "sticker_on"
    private const val KEY_SCALE = "sticker_scale"
    private const val KEY_ROT = "sticker_rot"
    private const val KEY_X = "sticker_x"
    private const val KEY_Y = "sticker_y"

    // Слой в пикселях, соответствует виджету 4×2 (около 360×136 dp при плотности 2).
    private const val W = 720
    private const val H = 272
    private const val MARGIN = 12f

    private var sourceStamp = 0L
    private var source: Bitmap? = null
    private var cacheKey: String? = null
    private var cacheBmp: Bitmap? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.filesDir, "sticker.png")

    fun hasFile(ctx: Context): Boolean = file(ctx).exists()

    fun isOn(ctx: Context): Boolean = hasFile(ctx) && prefs(ctx).getBoolean(KEY_ON, false)

    fun setOn(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ON, on).apply()
    }

    /** Размер в процентах от высоты виджета, от 10 до 100. */
    fun scale(ctx: Context): Int = prefs(ctx).getInt(KEY_SCALE, 40)

    fun setScale(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_SCALE, value.coerceIn(10, 100)).apply()
    }

    /** Поворот в градусах, от -180 до 180. */
    fun rotation(ctx: Context): Int = prefs(ctx).getInt(KEY_ROT, 0)

    fun setRotation(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_ROT, value.coerceIn(-180, 180)).apply()
    }

    /**
     * Положение центра наклейки в долях слоя (0..1). NaN означает правый верхний угол.
     * Задаётся перетаскиванием в предпросмотре в приложении.
     */
    fun posX(ctx: Context): Float = prefs(ctx).getFloat(KEY_X, Float.NaN)
    fun posY(ctx: Context): Float = prefs(ctx).getFloat(KEY_Y, Float.NaN)

    fun setPosition(ctx: Context, x: Float, y: Float) {
        prefs(ctx).edit().putFloat(KEY_X, x.coerceIn(0f, 1f)).putFloat(KEY_Y, y.coerceIn(0f, 1f)).apply()
    }

    fun remove(ctx: Context) {
        file(ctx).delete()
        setOn(ctx, false)
        source = null
        cacheKey = null
        cacheBmp = null
    }

    /** Копирует выбранную картинку в память приложения. Возвращает false, если файл не картинка. */
    fun importFrom(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                file(ctx).outputStream().use { input.copyTo(it) }
            }
            val ok = decode(file(ctx)) != null
            if (!ok) {
                file(ctx).delete()
                return false
            }
            source = null
            cacheKey = null
            setOn(ctx, true)
            true
        } catch (e: Exception) {
            file(ctx).delete()
            false
        }
    }

    private fun decode(f: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }

    private fun source(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        if (source == null || f.lastModified() != sourceStamp) {
            source = decode(f)
            sourceStamp = f.lastModified()
        }
        return source
    }

    /**
     * Слой наклейки под текущие размер и поворот. Если наклейка выключена и force не задан,
     * возвращает null. force нужен для предпросмотра в настройках.
     */
    fun layer(ctx: Context, force: Boolean = false): Bitmap? {
        if (!force && !isOn(ctx)) return null
        val src = source(ctx) ?: return null
        val sc = scale(ctx)
        val rot = rotation(ctx)
        val px = posX(ctx)
        val py = posY(ctx)
        val key = "$sourceStamp:$sc:$rot:$px:$py"
        if (key == cacheKey) return cacheBmp

        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val hPx = H * sc / 100f
        val wPx = hPx * src.width / src.height
        // Центр наклейки: по умолчанию правый верхний угол, иначе место, которое выбрали пальцем.
        val cx = if (px.isNaN()) W - MARGIN - wPx / 2f else px * W
        val cy = if (py.isNaN()) MARGIN + hPx / 2f else py * H
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rot.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, null, RectF(-wPx / 2f, -hPx / 2f, wPx / 2f, hPx / 2f), paint)
        canvas.restore()

        cacheKey = key
        cacheBmp = out
        return out
    }
}
