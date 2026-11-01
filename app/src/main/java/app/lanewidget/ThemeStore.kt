package app.lanewidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Цветовой пак: название и три базовых цвета в формате #RRGGBB. */
data class Pack(val name: String, val bg: String, val accent: String, val text: String)

/**
 * Хранение паков. Два встроенных (тёмная и светлая) нельзя удалить или перезаписать.
 * Свои паки сохраняются в настройках приложения.
 */
object ThemeStore {

    private const val PREFS = "lane_widget"
    private const val KEY_CUSTOM = "theme_custom"
    private const val KEY_ACTIVE = "theme_active"
    private val HEX = Regex("#[0-9A-Fa-f]{6}")

    val DARK = Pack("Тёмная", "#232323", "#F4A9C0", "#F2F2F2")
    val LIGHT = Pack("Светлая", "#FFFFFF", "#D96C8F", "#1A1A1A")
    private val BUILT_IN = listOf(DARK, LIGHT)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBuiltIn(name: String) = BUILT_IN.any { it.name == name }

    /** Принимает «#1e1e2e», «1e1e2e», пробелы по краям. Возвращает #RRGGBB или null. */
    fun normalize(s: String): String? {
        val t = s.trim().let { if (it.startsWith("#")) it else "#$it" }
        return if (HEX.matches(t)) t.uppercase() else null
    }

    fun custom(ctx: Context): List<Pack> {
        val raw = prefs(ctx).getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Pack(o.getString("name"), o.getString("bg"), o.getString("accent"), o.getString("text"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun all(ctx: Context): List<Pack> = BUILT_IN + custom(ctx)

    fun active(ctx: Context): Pack {
        val name = prefs(ctx).getString(KEY_ACTIVE, DARK.name)
        return all(ctx).firstOrNull { it.name == name } ?: DARK
    }

    fun setActive(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, name).apply()
    }

    /** Загружает активный пак в Palette. Вызывать перед отрисовкой экранов и виджета. */
    fun apply(ctx: Context) = Palette.load(active(ctx))

    /** Возвращает код ошибки («name» или «builtin») либо null, если всё сохранено. */
    fun save(ctx: Context, p: Pack): String? {
        if (p.name.isBlank()) return "name"
        if (isBuiltIn(p.name)) return "builtin"
        val list = custom(ctx).filter { it.name != p.name } + p
        writeCustom(ctx, list)
        return null
    }

    fun delete(ctx: Context, name: String) {
        if (isBuiltIn(name)) return
        writeCustom(ctx, custom(ctx).filter { it.name != name })
        if (active(ctx).name == name) setActive(ctx, DARK.name)
    }

    private fun writeCustom(ctx: Context, list: List<Pack>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("bg", it.bg)
                    .put("accent", it.accent)
                    .put("text", it.text),
            )
        }
        prefs(ctx).edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }
}
