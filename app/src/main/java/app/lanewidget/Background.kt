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
 * Фон виджета: прозрачность и своё фото 4×2. Фото обрезается под размер виджета без растягивания,
 * с приближением и сдвигом. Прозрачность работает для обложки, своего фото и цвета пака.
 */
object Background {
    private const val PREFS = "lane_widget"
    const val KEY_TRANS_ON = "trans_on"
    const val KEY_TRANS = "trans_value"   // прозрачность 0..90 %
    const val KEY_CUSTOM_ON = "bg_custom_on"
    const val KEY_ZOOM = "bg_zoom"        // приближение 100..300 %
    const val KEY_DX = "bg_dx"            // сдвиг -50..50 % ширины
    const val KEY_DY = "bg_dy"            // сдвиг -50..50 % высоты

    private var src: Bitmap? = null
    private var srcStamp = 0L
    private var cacheKey: String? = null
    private var cacheBmp: Bitmap? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.filesDir, "bg_custom.png")

    fun transparencyOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_TRANS_ON, false)
    fun transparency(ctx: Context): Int = prefs(ctx).getInt(KEY_TRANS, 50)
    fun zoom(ctx: Context): Int = prefs(ctx).getInt(KEY_ZOOM, 100)
    fun dx(ctx: Context): Int = prefs(ctx).getInt(KEY_DX, 0)
    fun dy(ctx: Context): Int = prefs(ctx).getInt(KEY_DY, 0)
    fun hasCustomFile(ctx: Context): Boolean = file(ctx).exists()
    fun customOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CUSTOM_ON, false) && file(ctx).exists()

    fun setBool(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    fun setInt(ctx: Context, key: String, value: Int) {
        prefs(ctx).edit().putInt(key, value).apply()
    }

    /** Помутнение фона 0..255. 0 — фото без затемнения. Накладывается поверх фото или обложки. */
    fun veil(ctx: Context): Int =
        if (transparencyOn(ctx)) (255 * transparency(ctx) / 100).coerceIn(0, 255) else 0

    /** Непрозрачность сплошной карточки пака: при помутнении она чуть прозрачнее. */
    fun alpha(ctx: Context): Int = 255 - veil(ctx)

    fun withAlphaArgb(argb: Int, alpha: Int): Int = (alpha shl 24) or (argb and 0xFFFFFF)

    fun removeCustom(ctx: Context) {
        file(ctx).delete()
        setBool(ctx, KEY_CUSTOM_ON, false)
        src = null
        cacheKey = null
        cacheBmp = null
    }

    /** Копирует фото в память приложения. Возвращает false, если файл не картинка. */
    fun importFrom(ctx: Context, uri: Uri): Boolean {
        val f = file(ctx)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { input.copyTo(it) }
            }
            if (decode(f) == null) {
                f.delete()
                false
            } else {
                src = null
                cacheKey = null
                setBool(ctx, KEY_CUSTOM_ON, true)
                true
            }
        } catch (e: Exception) {
            f.delete()
            false
        }
    }

    private fun decode(f: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun source(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val stamp = f.lastModified()
        if (src == null || stamp != srcStamp) {
            src = decode(f)
            srcStamp = stamp
        }
        return src
    }

    /**
     * Своё фото под размер w×h: заполняет всю область, края не пустые, лишнее обрезается.
     * Без force возвращает null, если фото не включено. force нужен для предпросмотра.
     */
    fun layer(ctx: Context, w: Int, h: Int, force: Boolean = false): Bitmap? {
        if (!force && !customOn(ctx)) return null
        val s = source(ctx) ?: return null
        if (w <= 0 || h <= 0) return null
        val key = "$srcStamp:$w:$h:${zoom(ctx)}:${dx(ctx)}:${dy(ctx)}"
        if (key == cacheKey) return cacheBmp

        val scale = maxOf(w.toFloat() / s.width, h.toFloat() / s.height) * zoom(ctx) / 100f
        val dw = s.width * scale
        val dh = s.height * scale
        var left = (w - dw) / 2f + dx(ctx) / 100f * w
        var top = (h - dh) / 2f + dy(ctx) / 100f * h
        // Не даём сдвинуть фото так, чтобы появился пустой край.
        left = left.coerceIn(w - dw, 0f)
        top = top.coerceIn(h - dh, 0f)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(out).drawBitmap(s, null, RectF(left, top, left + dw, top + dh), paint)
        cacheKey = key
        cacheBmp = out
        return out
    }

    /** Заполняет область w×h размытым фоном с обрезкой по краям, без растяжения. */
    fun fitCover(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        Canvas(out).drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
