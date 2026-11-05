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

    fun shown(ctx: Context, key: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, true)

    fun setShown(ctx: Context, key: String, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
}
