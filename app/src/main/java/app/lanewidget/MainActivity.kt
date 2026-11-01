package app.lanewidget

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Экран настройки: доступ к уведомлениям, выбор действия для кнопки «+», подсказка по установке. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var actionsBox: LinearLayout
    private lateinit var playersBox: LinearLayout
    private lateinit var batteryStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bg = Palette.screenBg.toArgb()
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(36), dp(28), dp(36))
        }

        content.addView(label(getString(R.string.app_name), 30f, Palette.text.toArgb(), bold = true))
        content.addView(
            label(getString(R.string.main_sub), 15f, Palette.dim.toArgb())
                .apply { setPadding(0, dp(8), 0, dp(28)) },
        )

        status = label("", 15f, Palette.pink.toArgb(), bold = true)
        content.addView(status)

        content.addView(
            pillButton(getString(R.string.grant_button)) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(14)
                bottomMargin = dp(28)
            },
        )

        content.addView(label(getString(R.string.steps), 15f, Palette.text.toArgb()))

        // Чтобы виджет не засыпал
        content.addView(
            label(getString(R.string.bg_title), 20f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(32), 0, dp(6)) },
        )
        content.addView(label(getString(R.string.bg_hint), 14f, Palette.dim.toArgb()))
        batteryStatus = label("", 14f, Palette.pink.toArgb(), bold = true)
            .apply { setPadding(0, dp(12), 0, 0) }
        content.addView(batteryStatus)
        content.addView(
            pillButton(getString(R.string.battery_button)) { requestBatteryExemption() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) },
        )
        content.addView(
            pillButton(getString(R.string.autostart_button)) { openAutostartSettings() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) },
        )

        // Какие плееры показывать
        content.addView(
            label(getString(R.string.players_title), 20f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(32), 0, dp(6)) },
        )
        content.addView(label(getString(R.string.players_hint), 14f, Palette.dim.toArgb()))
        playersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(playersBox)

        content.addView(
            label(getString(R.string.like_title), 20f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(32), 0, dp(6)) },
        )
        content.addView(label(getString(R.string.like_hint), 14f, Palette.dim.toArgb()))
        actionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(actionsBox)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(content)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val on = MediaRepo.hasAccess(this)
        status.text = getString(if (on) R.string.access_on else R.string.access_off)
        if (on) {
            // На случай, если система отвязала сервис: просим привязать заново.
            MediaRepo.rebind(this)
            // Приложение сейчас на экране, поэтому запуск службы-«якоря» разрешён.
            KeepAliveService.start(this)
        }
        val pm = getSystemService(PowerManager::class.java)
        val exempt = pm?.isIgnoringBatteryOptimizations(packageName) == true
        batteryStatus.text = getString(if (exempt) R.string.battery_on else R.string.battery_off)
        renderPlayers()
        renderActions()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun requestBatteryExemption() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        try {
            startActivity(direct)
        } catch (e: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    /** Экран автозапуска у производителей прошивок. Если не нашли, открываем страницу приложения. */
    private fun openAutostartSettings() {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        )
        for (cn in candidates) {
            try {
                startActivity(Intent().setComponent(cn))
                return
            } catch (_: Exception) {
            }
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")),
            )
        }
    }

    /** Все активные плееры, включая скрытые, с переключателем «показывать / скрыть». */
    private fun renderPlayers() {
        playersBox.removeAllViews()
        val list = if (MediaRepo.hasAccess(this)) MediaRepo.controllers(this, includeBlocked = true) else emptyList()
        if (list.isEmpty()) {
            playersBox.addView(
                label(getString(R.string.players_none), 14f, Palette.dim.toArgb())
                    .apply { setPadding(0, dp(12), 0, 0) },
            )
            return
        }
        for (c in list) {
            val hidden = Blocklist.isBlocked(this, c.packageName)
            val state = getString(if (hidden) R.string.player_hidden else R.string.player_shown)
            playersBox.addView(chip("${appLabel(c.packageName)}: $state", !hidden) {
                Blocklist.setBlocked(this, c.packageName, !hidden)
                MediaListener.instance?.reattach()
                CoroutineScope(Dispatchers.Main).launch { WidgetUpdater.refresh(applicationContext) }
                renderPlayers()
                renderActions()
            })
        }
    }

    private fun pillButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Palette.onPink.toArgb())
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        background = GradientDrawable().apply {
            setColor(Palette.pink.toArgb())
            cornerRadius = dp(26).toFloat()
        }
        setOnClickListener { onClick() }
    }

    /** Показывает дополнительные действия активных плееров и даёт выбрать то, что делает «+». */
    private fun renderActions() {
        actionsBox.removeAllViews()
        val list = if (MediaRepo.hasAccess(this)) MediaRepo.controllers(this) else emptyList()
        if (list.isEmpty()) {
            actionsBox.addView(
                label(getString(R.string.like_none), 14f, Palette.dim.toArgb())
                    .apply { setPadding(0, dp(12), 0, 0) },
            )
            return
        }
        for (c in list) {
            val actions = c.playbackState?.customActions.orEmpty()
            actionsBox.addView(
                label(appLabel(c.packageName), 15f, Palette.text.toArgb(), bold = true)
                    .apply { setPadding(0, dp(16), 0, dp(2)) },
            )
            if (actions.isEmpty()) {
                actionsBox.addView(label(getString(R.string.like_empty), 13f, Palette.dim.toArgb()))
                continue
            }
            val selected = MediaRepo.savedLike(this, c.packageName)
                ?: MediaRepo.findLikeAction(this, c)?.action
            for (a in actions) {
                actionsBox.addView(chip("${a.name}", a.action == selected) {
                    MediaRepo.saveLike(this, c.packageName, a.action)
                    renderActions()
                })
            }
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor((if (selected) Palette.onPink else Palette.text).toArgb())
        typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            setColor((if (selected) Palette.pink else Palette.card).toArgb())
            cornerRadius = dp(16).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
