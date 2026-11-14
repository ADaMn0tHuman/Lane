package app.lanewidget

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Настройки уведомления с музыкой. Открывается свайпом вправо на главном экране. */
class NotifySettingsActivity : Activity() {

    private lateinit var root: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.apply(this)
        val bg = Palette.screenBg.toArgb()
        window.statusBarColor = bg
        window.navigationBarColor = bg

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(24))
        }
        render()
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        })
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text(getString(R.string.notify_title), 22f, Palette.text.toArgb(), true))
        root.addView(text(getString(R.string.notify_back_hint), 13f, Palette.dim.toArgb(), false))

        root.addView(text(getString(R.string.notify_section_show), 16f, Palette.text.toArgb(), true).pad())
        toggle(NotifyPlayer.ON, R.string.notify_on_on, R.string.notify_on_off, false)
        toggle(NotifyPlayer.COVER, R.string.notify_cover_on, R.string.notify_cover_off, true)
        toggle(NotifyPlayer.BAR, R.string.notify_bar_on, R.string.notify_bar_off, true)
        toggle(NotifyPlayer.SKIP, R.string.notify_skip_on, R.string.notify_skip_off, true)
        toggle(NotifyPlayer.LOCK, R.string.notify_lock_on, R.string.notify_lock_off, true)

        root.addView(text(getString(R.string.notify_colors), 16f, Palette.text.toArgb(), true).pad())
        colorEditor(NotifyPlayer.BG, R.string.notify_bg_label)
        colorEditor(NotifyPlayer.FG, R.string.notify_fg_label)
        colorEditor(NotifyPlayer.ACC, R.string.notify_accent_label)
    }

    private fun toggle(key: String, onRes: Int, offRes: Int, default: Boolean) {
        val on = NotifyPlayer.flag(this, key, default)
        root.addView(
            pill(getString(if (on) onRes else offRes)) {
                NotifyPlayer.setFlag(this, key, !on)
                applyNow()
                render()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )
    }

    private fun colorEditor(key: String, labelRes: Int) {
        root.addView(text(getString(labelRes), 13f, Palette.dim.toArgb(), false).pad())
        val current = NotifyPlayer.color(this, key)
        val field = EditText(this).apply {
            setText(current?.let { String.format("#%06X", it and 0xFFFFFF) } ?: "")
            hint = "#RRGGBB"
            setTextColor(Palette.text.toArgb())
            setHintTextColor(Palette.dim.toArgb())
            setBackgroundColor(Palette.track.toArgb())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine()
        }
        root.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        root.addView(
            pill(getString(R.string.btn_color_apply)) {
                val n = ThemeStore.normalize(field.text.toString())
                if (n == null) {
                    Toast.makeText(this, R.string.theme_bad_hex, Toast.LENGTH_LONG).show()
                } else {
                    NotifyPlayer.setColor(this, key, n)
                    applyNow()
                    render()
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) },
        )
        root.addView(
            pill(getString(R.string.btn_color_reset)) {
                NotifyPlayer.setColor(this, key, null)
                applyNow()
                render()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) },
        )
    }

    /** Сразу пересобрать уведомление и виджет с новыми настройками. */
    private fun applyNow() {
        CoroutineScope(Dispatchers.Main).launch { WidgetUpdater.refresh(applicationContext) }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun TextView.pad(): TextView {
        setPadding(0, dp(18), 0, dp(4))
        return this
    }

    private fun pill(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Palette.onPink.toArgb())
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        background = GradientDrawable().apply {
            setColor(Palette.pink.toArgb())
            cornerRadius = dp(24).toFloat()
        }
        setOnClickListener { onClick() }
    }
}
