package app.lanewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.PowerManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран диагностики. Показывает, что виджет видит и что происходит внутри, чтобы искать причину
 * проблем по фактам. Текст можно скопировать и отправить.
 */
class DiagnosticsActivity : Activity() {

    private lateinit var out: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Palette.screenBg.toArgb()
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(24))
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(pill(getString(R.string.diag_refresh)) { render() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        buttons.addView(pill(getString(R.string.diag_copy)) { copy() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        out = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(Palette.text.toArgb())
            setTextIsSelectable(true)
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(out)

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(bg)
                addView(root)
            },
        )
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun pill(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
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

    private fun copy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Lane Widget", out.text))
        Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }

    private fun yn(v: Boolean) = if (v) "да" else "нет"

    private fun stateName(s: Int?): String {
        val name = when (s) {
        PlaybackState.STATE_NONE -> "NONE"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
        PlaybackState.STATE_REWINDING -> "REWINDING"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_ERROR -> "ERROR"
        PlaybackState.STATE_CONNECTING -> "CONNECTING"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
        null -> "нет данных"
        else -> "?"
        }
        return if (s != null) "$name ($s)" else name
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun time(ms: Long): String =
        if (ms <= 0) "ещё не было" else {
            val ago = (System.currentTimeMillis() - ms) / 1000
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms)) + " ($ago с назад)"
        }

    private fun render() {
        val sb = StringBuilder()
        val has = MediaRepo.hasAccess(this)
        val pm = getSystemService(PowerManager::class.java)
        val awm = AppWidgetManager.getInstance(this)
        val big = awm.getAppWidgetIds(ComponentName(this, LaneWidgetReceiver::class.java)).size
        val small = awm.getAppWidgetIds(ComponentName(this, LaneCompactWidgetReceiver::class.java)).size

        sb.appendLine("ОБЩЕЕ")
        sb.appendLine("доступ к уведомлениям: ${yn(has)}")
        sb.appendLine("сервис слежения подключён: ${yn(MediaListener.connected)}")
        sb.appendLine("фоновая служба работает: ${yn(KeepAliveService.running)}")
        KeepAliveService.lastError?.let { sb.appendLine("  ошибка службы: $it") }
        sb.appendLine("батарея без ограничений: ${yn(pm?.isIgnoringBatteryOptimizations(packageName) == true)}")
        sb.appendLine("виджетов 4×2: $big, компактных 4×1: $small")
        sb.appendLine()
        sb.appendLine("ОБНОВЛЕНИЯ ВИДЖЕТА")
        sb.appendLine("успешных: ${WidgetUpdater.updates}, через запасной путь: ${WidgetUpdater.fallbacks}")
        sb.appendLine("последнее удачное: ${time(WidgetUpdater.lastOkAt)}")
        WidgetUpdater.lastError?.let { sb.appendLine("последняя ошибка: $it") }
        sb.appendLine("идёт смена обложки: ${yn(CoverFade.running)}")
        sb.appendLine("последнее нажатие на виджет: ${OpenPlayerActivity.lastResult}")
        sb.appendLine()

        val all = if (has) MediaRepo.controllers(this, includeBlocked = true) else emptyList()
        val chosen = MediaRepo.pick(this, MediaRepo.controllers(this))
        sb.appendLine("ВЫБРАННЫЙ ПЛЕЕР")
        sb.appendLine(chosen?.let { "${appLabel(it.packageName)} (${it.packageName})" } ?: "нет")
        sb.appendLine()
        sb.appendLine("АКТИВНЫЕ ПЛЕЕРЫ (${all.size})")
        if (all.isEmpty()) sb.appendLine("нет активных сессий")
        all.forEach { sb.append(describe(it, it == chosen)) }

        out.text = sb.toString()
    }

    private fun describe(c: MediaController, chosen: Boolean): String {
        val sb = StringBuilder()
        val blocked = Blocklist.isBlocked(this, c.packageName)
        val st = c.playbackState
        sb.appendLine("— ${appLabel(c.packageName)} (${c.packageName})")
        sb.appendLine("  скрыт: ${yn(blocked)}${if (chosen) ", ВЫБРАН" else ""}")
        sb.appendLine("  состояние: ${stateName(st?.state)}")
        val dur = c.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        sb.appendLine("  позиция: ${MediaRepo.formatTime(st?.position ?: 0)} из ${MediaRepo.formatTime(dur)}")
        sb.appendLine("  название: ${c.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)}")
        if (!blocked) {
            val i = PlayerControls.info(this, c)
            val (sMode, rMode) = PlayerControls.debugModes(this, c)
            sb.appendLine("  нравится: ${yn(i.liked)}")
            sb.appendLine("  shuffle: поддержка ${yn(i.shuffleSupported)}, включён ${yn(i.shuffleOn)} (сырое значение $sMode)")
            sb.appendLine("  repeat: поддержка ${yn(i.repeatSupported)}, режим ${i.repeatMode} (сырое значение $rMode)")
            sb.appendLine("  скачивание: поддержка ${yn(i.downloadSupported)}, скачано ${yn(i.downloaded)}")
            sb.appendLine("  стиль рейтинга: ${PlayerControls.debugRatingStyle(c) ?: "нет"}")
        }
        sb.appendLine("  экран плеера (sessionActivity): ${yn(c.sessionActivity != null)}")
        val actions = st?.customActions.orEmpty()
        sb.appendLine("  действия плеера (${actions.size}):")
        actions.forEach { sb.appendLine("    • ${it.action} | ${it.name}") }
        sb.appendLine()
        return sb.toString()
    }
}
