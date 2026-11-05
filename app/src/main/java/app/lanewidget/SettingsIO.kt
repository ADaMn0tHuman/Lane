package app.lanewidget

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File

/** Экспорт и импорт всех настроек одним JSON-файлом, включая своё фото фона. */
object SettingsIO {
    private const val PREFS = "lane_widget"
    private const val BG_FILE = "bg_custom.png"

    fun export(ctx: Context, uri: Uri): Boolean = try {
        val values = JSONObject()
        for ((k, v) in ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) {
            val entry = JSONObject()
            when (v) {
                is Boolean -> values.put(k, entry.put("t", "b").put("v", v))
                is Int -> values.put(k, entry.put("t", "i").put("v", v))
                is Long -> values.put(k, entry.put("t", "l").put("v", v))
                is Float -> values.put(k, entry.put("t", "f").put("v", v.toDouble()))
                is String -> values.put(k, entry.put("t", "s").put("v", v))
                else -> Unit
            }
        }
        val root = JSONObject().put("version", 1).put("prefs", values)
        val bg = File(ctx.filesDir, BG_FILE)
        if (bg.exists()) root.put("bg", Base64.encodeToString(bg.readBytes(), Base64.NO_WRAP))
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(root.toString().toByteArray()) }
        true
    } catch (e: Exception) {
        false
    }

    fun import(ctx: Context, uri: Uri): Boolean = try {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: return false
        val root = JSONObject(text)
        val values = root.getJSONObject("prefs")
        val edit = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = values.getJSONObject(k)
            when (o.getString("t")) {
                "b" -> edit.putBoolean(k, o.getBoolean("v"))
                "i" -> edit.putInt(k, o.getInt("v"))
                "l" -> edit.putLong(k, o.getLong("v"))
                "f" -> edit.putFloat(k, o.getDouble("v").toFloat())
                "s" -> edit.putString(k, o.getString("v"))
            }
        }
        edit.commit()
        val bg = File(ctx.filesDir, BG_FILE)
        if (root.has("bg")) bg.writeBytes(Base64.decode(root.getString("bg"), Base64.NO_WRAP)) else bg.delete()
        true
    } catch (e: Exception) {
        false
    }
}
