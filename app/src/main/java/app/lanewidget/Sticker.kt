package app.lanewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Одна наклейка. scale: высота в процентах от высоты виджета. rot: поворот в градусах.
 * x, y: центр в долях слоя (0..1). NaN означает правый верхний угол (для старых наклеек).
 */
data class StickerItem(val id: String, val scale: Int, val rot: Int, val x: Float, val y: Float)

/**
 * Наклейки на большом виджете. Картинки с прозрачным фоном (PNG или WebP) лежат во внутренней памяти
 * приложения. Слой строится под фактический размер виджета, поэтому картинка не растягивается.
 * Всё, что выходит за края, обрезается.
 */
object Sticker {

    private const val PREFS = "lane_widget"
    private const val KEY_ON = "sticker_on"
    private const val KEY_LIST = "sticker_list"
    private const val KEY_SEL = "sticker_sel"

    // Ключи первой версии (одна наклейка). Нужны только для переноса.
    private const val OLD_SCALE = "sticker_scale"
    private const val OLD_ROT = "sticker_rot"
    private const val OLD_X = "sticker_x"
    private const val OLD_Y = "sticker_y"

    const val MAX_ITEMS = 5

    private val sources = HashMap<String, Pair<Long, Bitmap>>()
    private var cacheKey: String? = null
    private var cacheBmp: Bitmap? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(ctx: Context, id: String) = File(ctx.filesDir, "sticker_$id.png")

    fun isOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, false) && items(ctx).isNotEmpty()

    fun setOn(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ON, on).apply()
    }

    fun hasAny(ctx: Context): Boolean = items(ctx).isNotEmpty()

    fun items(ctx: Context): List<StickerItem> {
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return migrate(ctx)
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StickerItem(
                    o.getString("id"),
                    o.getInt("scale"),
                    o.getInt("rot"),
                    fromStored(o.getDouble("x")),
                    fromStored(o.getDouble("y")),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fromStored(v: Double): Float = if (v < 0) Float.NaN else v.toFloat()
    private fun toStored(v: Float): Double = if (v.isNaN()) -1.0 else v.toDouble()

    private fun saveItems(ctx: Context, list: List<StickerItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("scale", it.scale)
                    .put("rot", it.rot)
                    .put("x", toStored(it.x))
                    .put("y", toStored(it.y)),
            )
        }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** Переносит одну наклейку из первой версии в список. */
    private fun migrate(ctx: Context): List<StickerItem> {
        val old = File(ctx.filesDir, "sticker.png")
        if (!old.exists()) return emptyList()
        val p = prefs(ctx)
        val item = StickerItem(
            "0",
            p.getInt(OLD_SCALE, 40),
            p.getInt(OLD_ROT, 0),
            p.getFloat(OLD_X, Float.NaN),
            p.getFloat(OLD_Y, Float.NaN),
        )
        old.renameTo(file(ctx, "0"))
        saveItems(ctx, listOf(item))
        return listOf(item)
    }

    fun selected(ctx: Context): StickerItem? {
        val list = items(ctx)
        val id = prefs(ctx).getString(KEY_SEL, null)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun setSelected(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_SEL, id).apply()
    }

    /** Добавляет картинку. Возвращает false, если это не картинка или достигнут лимит. */
    fun add(ctx: Context, uri: Uri): Boolean {
        val list = items(ctx)
        if (list.size >= MAX_ITEMS) return false
        val id = System.currentTimeMillis().toString()
        val f = file(ctx, id)
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { input.copyTo(it) }
            }
            if (decode(f) == null) {
                f.delete()
                false
            } else {
                // Новая наклейка появляется в центре: её видно сразу, а не в углу под другими.
                saveItems(ctx, list + StickerItem(id, 40, 0, 0.5f, 0.5f))
                setSelected(ctx, id)
                setOn(ctx, true)
                true
            }
        } catch (e: Exception) {
            f.delete()
            false
        }
    }

    fun update(ctx: Context, id: String, change: (StickerItem) -> StickerItem) {
        saveItems(ctx, items(ctx).map { if (it.id == id) change(it) else it })
    }

    fun remove(ctx: Context, id: String) {
        file(ctx, id).delete()
        sources.remove(id)
        val left = items(ctx).filter { it.id != id }
        saveItems(ctx, left)
        if (left.isEmpty()) setOn(ctx, false)
        if (prefs(ctx).getString(KEY_SEL, null) == id) {
            prefs(ctx).edit().remove(KEY_SEL).apply()
        }
        cacheKey = null
        cacheBmp = null
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

    private fun source(ctx: Context, id: String): Bitmap? {
        val f = file(ctx, id)
        if (!f.exists()) return null
        val stamp = f.lastModified()
        sources[id]?.let { (oldStamp, bmp) -> if (oldStamp == stamp) return bmp }
        val b = decode(f) ?: return null
        sources[id] = stamp to b
        return b
    }

    /**
     * Слой всех наклеек размером w×h пикселей. Без force возвращает null, если наклейки выключены.
     * force нужен для предпросмотра в настройках.
     */
    fun layer(ctx: Context, w: Int, h: Int, force: Boolean = false): Bitmap? {
        if (!force && !isOn(ctx)) return null
        val list = items(ctx)
        if (list.isEmpty() || w <= 0 || h <= 0) return null

        val sig = buildString {
            append(w).append('x').append(h)
            list.forEach {
                append('|').append(it.id).append(':').append(it.scale).append(':').append(it.rot)
                    .append(':').append(it.x).append(':').append(it.y)
                    .append(':').append(file(ctx, it.id).lastModified())
            }
        }
        if (sig == cacheKey) return cacheBmp

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val margin = h * 0.045f
        for (item in list) {
            val src = source(ctx, item.id) ?: continue
            val hPx = h * item.scale / 100f
            val wPx = hPx * src.width / src.height
            // Центр: по умолчанию правый верхний угол, иначе место, выбранное пальцем.
            val cx = if (item.x.isNaN()) w - margin - wPx / 2f else item.x * w
            val cy = if (item.y.isNaN()) margin + hPx / 2f else item.y * h
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(item.rot.toFloat())
            canvas.drawBitmap(src, null, RectF(-wPx / 2f, -hPx / 2f, wPx / 2f, hPx / 2f), paint)
            canvas.restore()
        }
        cacheKey = sig
        cacheBmp = out
        return out
    }
}
