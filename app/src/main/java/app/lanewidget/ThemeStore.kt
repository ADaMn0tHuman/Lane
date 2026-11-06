package app.lanewidget

import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray
import org.json.JSONObject

/** Цветовой пак. Точка необязательна: пустая значит цвет текста. */
data class Pack(
    val name: String,
    val bg: String,
    val accent: String,
    val text: String,
    val dot: String = "",
    /** Цвета кнопок и режимов: имя (prev, like, shuffle_on, ...) -> #RRGGBB. Пусто — цвета по умолчанию. */
    val colors: Map<String, String> = emptyMap(),
)

object ThemeStore {

    private const val PREFS = "lane_widget"
    private const val KEY_CUSTOM = "theme_custom"
    private const val KEY_ACTIVE = "theme_active"
    private val HEX = Regex("#[0-9A-Fa-f]{6}")

    const val FLAG_ACCENT = "cover_accent"
    const val FLAG_BLUR = "cover_blur"
    const val FLAG_AUTO = "theme_auto"

    val DARK = Pack("Тёмная", "#232323", "#F4A9C0", "#F2F2F2")
    val LIGHT = Pack("Светлая", "#FFFFFF", "#D96C8F", "#1A1A1A")
    private val BUILT_IN = listOf(DARK, LIGHT)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBuiltIn(name: String) = BUILT_IN.any { it.name == name }

    fun flag(ctx: Context, key: String): Boolean = prefs(ctx).getBoolean(key, false)

    fun setFlag(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    /** Принимает «#1e1e2e», «1e1e2e», пробелы по краям. Возвращает #RRGGBB или null. */
    fun normalize(s: String): String? {
        val t = s.trim().let { if (it.startsWith("#")) it else "#$it" }
        return if (HEX.matches(t)) t.uppercase() else null
    }

    /** Строка для обмена: «Название: #фон #акцент #текст [#точка] | имя=#цвет ...». */
    fun encode(p: Pack): String = buildString {
        append(p.name).append(": ").append(p.bg).append(' ').append(p.accent).append(' ').append(p.text)
        if (p.dot.isNotEmpty()) append(' ').append(p.dot)
        if (p.colors.isNotEmpty()) {
            append(" |")
            p.colors.toSortedMap().forEach { (k, v) -> append(' ').append(k).append('=').append(v) }
        }
    }

    fun decode(line: String): Pack? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        val name = line.substring(0, idx).trim()
        val halves = line.substring(idx + 1).split('|', limit = 2)
        val parts = halves[0].trim().split(' ', '\t').filter { it.isNotEmpty() }
        if (name.isEmpty() || (parts.size != 3 && parts.size != 4)) return null
        val bg = normalize(parts[0]) ?: return null
        val accent = normalize(parts[1]) ?: return null
        val text = normalize(parts[2]) ?: return null
        val dot = if (parts.size == 4) (normalize(parts[3]) ?: return null) else ""
        val colors = HashMap<String, String>()
        if (halves.size == 2) {
            for (tok in halves[1].trim().split(' ', '\t').filter { it.isNotEmpty() }) {
                val eq = tok.indexOf('=')
                if (eq <= 0) return null
                colors[tok.substring(0, eq)] = normalize(tok.substring(eq + 1)) ?: return null
            }
        }
        return Pack(name, bg, accent, text, dot, colors)
    }

    /** Текущие цвета кнопок и режимов, чтобы сохранить их в пак. */
    fun currentColors(ctx: Context): Map<String, String> {
        val out = HashMap<String, String>()
        for ((k, v) in prefs(ctx).all) {
            if (k.startsWith("color_") && v is String) out[k.removePrefix("color_")] = v
        }
        return out
    }

    /** Заменяет цвета кнопок и режимов цветами пака. */
    private fun applyColors(ctx: Context, colors: Map<String, String>) {
        val p = prefs(ctx)
        val e = p.edit()
        for (k in p.all.keys) if (k.startsWith("color_")) e.remove(k)
        for ((name, hex) in colors) e.putString("color_$name", hex)
        e.apply()
    }

    private fun colorsOf(o: JSONObject): Map<String, String> {
        val out = HashMap<String, String>()
        o.optJSONObject("colors")?.let { c -> c.keys().forEach { k -> out[k] = c.getString(k) } }
        return out
    }

    fun custom(ctx: Context): List<Pack> {
        val raw = prefs(ctx).getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Pack(
                    o.getString("name"),
                    o.getString("bg"),
                    o.getString("accent"),
                    o.getString("text"),
                    o.optString("dot", ""),
                    colorsOf(o),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun all(ctx: Context): List<Pack> = BUILT_IN + custom(ctx)

    /** Если включён авто-режим, пак выбирается по системной теме: тёмная или светлая. */
    fun active(ctx: Context): Pack {
        if (flag(ctx, FLAG_AUTO)) {
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return if (night) DARK else LIGHT
        }
        val name = prefs(ctx).getString(KEY_ACTIVE, DARK.name)
        return all(ctx).firstOrNull { it.name == name } ?: DARK
    }

    fun setActive(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, name).apply()
        // Активация пака ставит его цвета кнопок и режимов.
        all(ctx).firstOrNull { it.name == name }?.let { applyColors(ctx, it.colors) }
    }

    /** Загружает активный пак в Palette. Вызывается перед отрисовкой экранов и виджета. */
    fun apply(ctx: Context) {
        Palette.load(active(ctx))
    }

    /** Возвращает код ошибки («name» или «builtin») либо null, если всё сохранено. */
    fun save(ctx: Context, p: Pack): String? {
        if (p.name.isBlank()) return "name"
        if (isBuiltIn(p.name)) return "builtin"
        writeCustom(ctx, custom(ctx).filter { it.name != p.name } + p)
        return null
    }

    fun delete(ctx: Context, name: String) {
        if (isBuiltIn(name)) return
        writeCustom(ctx, custom(ctx).filter { it.name != name })
        if (prefs(ctx).getString(KEY_ACTIVE, null) == name) setActive(ctx, DARK.name)
    }

    private fun writeCustom(ctx: Context, list: List<Pack>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("bg", it.bg)
                    .put("accent", it.accent)
                    .put("text", it.text)
                    .put("dot", it.dot)
                    .put("colors", JSONObject(it.colors)),
            )
        }
        prefs(ctx).edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }
}
