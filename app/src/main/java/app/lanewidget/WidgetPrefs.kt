package app.lanewidget

import android.content.Context

/** Видимость кнопок виджета. По умолчанию все включены. Кнопка «играть» всегда на месте. */
object WP {
    private const val PREFS = "lane_widget"
    const val LIKE = "show_like"
    const val DOWNLOAD = "show_download"
    const val REPEAT = "show_repeat"
    const val SHUFFLE = "show_shuffle"
    const val SWITCH = "show_switch"
    const val PREV = "show_prev"
    const val NEXT = "show_next"
    const val REMAINING = "time_remaining"
    const val SLOW = "slow_tick"
    const val AUTO_BTN = "auto_btn_color"

    fun shown(ctx: Context, key: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, true)

    fun setShown(ctx: Context, key: String, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    fun flag(ctx: Context, key: String, default: Boolean): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, default)

    fun setFlag(ctx: Context, key: String, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    /** Свой цвет кнопки или null, если цвет по умолчанию. name — команда кнопки (prev, next, like, ..., toggle). */
    fun color(ctx: Context, name: String): Int? {
        val hex = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("color_$name", null) ?: return null
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (e: Exception) {
            null
        }
    }

    /** Сохраняет цвет кнопки в формате #RRGGBB. null возвращает цвет по умолчанию. */
    fun setColor(ctx: Context, name: String, hex: String?) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (hex == null) e.remove("color_$name") else e.putString("color_$name", hex)
        e.apply()
    }
}
