package app.lanewidget

import androidx.compose.ui.graphics.Color

/**
 * Текущие цвета. Три базовых (фон, акцент, текст) задаёт выбранный пак,
 * остальные оттенки вычисляются из них. Акцент может быть заменён цветом обложки (виджет).
 */
object Palette {
    private var _bg = 0xFF232323.toInt()
    private var _accent = 0xFFF4A9C0.toInt()
    private var _text = 0xFFF2F2F2.toInt()
    private var _override: Int? = null

    fun load(p: Pack) {
        _bg = android.graphics.Color.parseColor(p.bg)
        _accent = android.graphics.Color.parseColor(p.accent)
        _text = android.graphics.Color.parseColor(p.text)
        _override = null
    }

    /** Акцент из обложки или null, чтобы брать акцент пака. */
    fun setAccent(c: Int?) {
        _override = c
    }

    private val accentInt: Int get() = _override ?: _accent

    private fun lerp(x: Int, y: Int, t: Float) = (x + (y - x) * t).toInt()

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = lerp((a shr 16) and 0xFF, (b shr 16) and 0xFF, t)
        val g = lerp((a shr 8) and 0xFF, (b shr 8) and 0xFF, t)
        val bl = lerp(a and 0xFF, b and 0xFF, t)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun luminance(c: Int): Float {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    val card: Color get() = Color(_bg)
    val screenBg: Color get() = Color(_bg)
    val cover: Color get() = Color(mix(_bg, _text, 0.07f))
    val track: Color get() = Color(mix(_bg, _text, 0.2f))
    val muted: Color get() = Color(mix(_text, _bg, 0.7f))
    val dim: Color get() = Color(mix(_text, _bg, 0.45f))
    val text: Color get() = Color(_text)
    val pink: Color get() = Color(accentInt)
    val onPink: Color get() = if (luminance(accentInt) > 0.55f) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
}
