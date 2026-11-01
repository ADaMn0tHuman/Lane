package app.lanewidget

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb

/** Один простой экран: выдать доступ к уведомлениям и подсказка, как добавить виджет. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bg = Palette.screenBg.toArgb()
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        root.addView(label(getString(R.string.app_name), 30f, Palette.text.toArgb(), bold = true))
        root.addView(label(getString(R.string.main_sub), 15f, Palette.dim.toArgb()).apply { setPadding(0, dp(8), 0, dp(32)) })

        status = label("", 15f, Palette.pink.toArgb(), bold = true)
        root.addView(status)

        val button = Button(this).apply {
            text = getString(R.string.grant_button)
            isAllCaps = false
            setTextColor(Palette.onPink.toArgb())
            typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(Palette.pink.toArgb())
                cornerRadius = dp(26).toFloat()
            }
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        root.addView(
            button,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(14)
                bottomMargin = dp(32)
            },
        )

        root.addView(label(getString(R.string.steps), 15f, Palette.text.toArgb()))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val on = MediaRepo.hasAccess(this)
        status.text = getString(if (on) R.string.access_on else R.string.access_off)
        if (on) {
            // На случай, если система отвязала сервис: просим привязать заново.
            NotificationListenerService.requestRebind(ComponentName(this, MediaListener::class.java))
        }
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
