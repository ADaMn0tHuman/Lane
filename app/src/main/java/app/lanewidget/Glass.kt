package app.lanewidget

import android.content.Context

/**
 * Стиль «Стекло». В Glance нет размытия, поэтому стекло — это полупрозрачные элементы
 * и кнопки-пузыри с тонкой светлой обводкой. Каждый элемент включается отдельно.
 */
object Glass {
    private const val PREFS = "lane_widget"
    const val KEY_STYLE = "widget_style"      // "classic" или "glass"
    const val BUTTONS = "glass_buttons"
    const val BG = "glass_bg"
    const val BAR = "glass_bar"
    const val COVER = "glass_cover"
    private const val BG_MAX_ALPHA = 150       // около 60 % непрозрачности фона в режиме стекла

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getString(KEY_STYLE, "classic") == "glass"

    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putString(KEY_STYLE, if (value) "glass" else "classic").apply()
    }

    /** Элемент стекла включён по умолчанию. */
    fun on(ctx: Context, key: String): Boolean = prefs(ctx).getBoolean(key, true)

    fun setOn(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    /** Альфа фона с учётом стекла: не выше 60 %, если фон в стекле. */
    fun bgAlpha(ctx: Context, base: Int): Int =
        if (enabled(ctx) && on(ctx, BG)) minOf(base, BG_MAX_ALPHA) else base

    /** Передаёт флаги стекла в Palette. Вызывается вместе с выбором пака. */
    fun apply(ctx: Context) {
        val e = enabled(ctx)
        Palette.setGlass(
            btn = e && on(ctx, BUTTONS),
            bar = e && on(ctx, BAR),
            cover = e && on(ctx, COVER),
        )
    }
}
