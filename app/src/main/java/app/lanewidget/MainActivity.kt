package app.lanewidget

import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
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
    private lateinit var serviceStatus: TextView
    private lateinit var palettesBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.apply(this)
        UpdateWorker.schedule(this)

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

        // Группа: работа в фоне
        content.addView(
            label(getString(R.string.group_bg), 20f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(32), 0, dp(6)) },
        )
        content.addView(label(getString(R.string.bg_hint), 14f, Palette.dim.toArgb()))
        batteryStatus = label("", 14f, Palette.pink.toArgb(), bold = true)
            .apply { setPadding(0, dp(12), 0, 0) }
        content.addView(batteryStatus)
        serviceStatus = label("", 14f, Palette.pink.toArgb(), bold = true)
            .apply { setPadding(0, dp(6), 0, 0) }
        content.addView(serviceStatus)
        content.addView(
            pillButton(getString(R.string.battery_button)) { requestBatteryExemption() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) },
        )
        content.addView(
            pillButton(getString(R.string.autostart_button)) { openAutostartSettings() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) },
        )

        // Группа: внешний вид. Подгруппы строятся в renderPalettes.
        content.addView(
            label(getString(R.string.group_look), 20f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(32), 0, dp(6)) },
        )
        palettesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(palettesBox)

        // Группа: плееры
        content.addView(
            label(getString(R.string.players_title), 20f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(32), 0, dp(6)) },
        )
        content.addView(label(getString(R.string.players_hint), 14f, Palette.dim.toArgb()))
        playersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(playersBox)

        // Группа: энергия, обновления, настройки
        content.addView(
            label(getString(R.string.group_misc), 20f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(32), 0, dp(6)) },
        )
        val misc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        misc.addView(
            label(getString(R.string.misc_energy), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(4), 0, dp(4)) },
        )
        val slowOn = WP.flag(this, WP.SLOW, false)
        misc.addView(chip(getString(R.string.slow_chip), slowOn) {
            WP.setFlag(this, WP.SLOW, !slowOn)
            recreate()
        })
        misc.addView(
            label(getString(R.string.misc_update), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        misc.addView(
            pillButton(getString(R.string.update_button)) { UpdateChecker.check(this) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(8) },
        )
        misc.addView(
            pillButton(getString(R.string.diag_button)) { startActivity(Intent(this, DiagnosticsActivity::class.java)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(8) },
        )
        // Экспорт и импорт всех настроек
        misc.addView(
            label(getString(R.string.settings_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        misc.addView(
            pillButton(getString(R.string.settings_export)) { createFile(reqExport, "lane-widget-settings.json") },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )
        misc.addView(
            pillButton(getString(R.string.settings_import)) { openJson(reqImport) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )

        misc.addView(
            pillButton(getString(R.string.reset_button)) { confirmReset() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(16) },
        )
        makeCollapsible(misc)
        content.addView(misc)

        makeCollapsible(content)
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
            KeepAliveService.ensure(this)
        }
        val pm = getSystemService(PowerManager::class.java)
        val exempt = pm?.isIgnoringBatteryOptimizations(packageName) == true
        batteryStatus.text = getString(if (exempt) R.string.battery_on else R.string.battery_off)
        showServiceStatus()
        // Служба поднимается не мгновенно, поэтому смотрим статус ещё раз через 3,5 секунды.
        serviceStatus.postDelayed({ showServiceStatus() }, 3500)
        renderPlayers()
        renderPalettes()
    }

    private fun showServiceStatus() {
        if (!::serviceStatus.isInitialized) return
        val err = KeepAliveService.lastError
        serviceStatus.text = when {
            KeepAliveService.running -> getString(R.string.service_on)
            err != null -> getString(R.string.service_error, err)
            else -> getString(R.string.service_off)
        }
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

    /** Список паков, кнопка удаления своего пака и форма создания нового. */
    private fun renderPalettes() {
        palettesBox.removeAllViews()
        palettesBox.addView(
            label(getString(R.string.theme_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(4), 0, dp(4)) },
        )
        val active = ThemeStore.active(this)
        for (p in ThemeStore.all(this)) {
            val isOn = p.name == active.name
            palettesBox.addView(
                chip(if (isOn) "${p.name} ✓" else p.name, isOn) {
                    ThemeStore.setActive(this, p.name)
                    refreshWidgets()
                    recreate()
                },
            )
        }
        if (!ThemeStore.isBuiltIn(active.name)) {
            palettesBox.addView(
                pillButton(getString(R.string.theme_delete)) {
                    ThemeStore.delete(this, active.name)
                    refreshWidgets()
                    recreate()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) },
            )
        }

        palettesBox.addView(
            label(getString(R.string.cover_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        for ((key, res) in listOf(
            ThemeStore.FLAG_ACCENT to R.string.cover_accent,
            ThemeStore.FLAG_BLUR to R.string.cover_blur,
            ThemeStore.FLAG_AUTO to R.string.theme_auto,
        )) {
            val on = ThemeStore.flag(this, key)
            palettesBox.addView(
                chip(if (on) "${getString(res)} ✓" else getString(res), on) {
                    ThemeStore.setFlag(this, key, !on)
                    refreshWidgets()
                    renderPalettes()
                },
            )
        }

        // Кнопки виджета: каждую можно скрыть. По умолчанию все включены.
        palettesBox.addView(
            label(getString(R.string.buttons_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        palettesBox.addView(label(getString(R.string.buttons_hint), 13f, Palette.dim.toArgb()))
        val buttonKeys = listOf(
            WP.LIKE to R.string.btn_like,
            WP.DOWNLOAD to R.string.btn_download,
            WP.REPEAT to R.string.btn_repeat,
            WP.SHUFFLE to R.string.btn_shuffle,
            WP.SWITCH to R.string.btn_switch,
            WP.PREV to R.string.btn_prev,
            WP.NEXT to R.string.btn_next,
        )
        for ((key, res) in buttonKeys) {
            val on = WP.shown(this, key)
            palettesBox.addView(chip(getString(res), on) {
                WP.setShown(this, key, !on)
                refreshWidgets()
                renderPalettes()
            })
        }

        // Цвет кнопки: одна подгруппа на все кнопки (значок приложения не меняем)
        palettesBox.addView(
            label(getString(R.string.btn_color_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        val autoOn = WP.flag(this, WP.AUTO_BTN, false)
        palettesBox.addView(chip(if (autoOn) getString(R.string.auto_btn_on) else getString(R.string.auto_btn_off), autoOn) {
            WP.setFlag(this, WP.AUTO_BTN, !autoOn)
            refreshWidgets()
            renderPalettes()
        })
        if (autoOn) {
            palettesBox.addView(label(getString(R.string.auto_btn_hint), 13f, Palette.dim.toArgb()))
        }
        val buttonNames = listOf(
            "prev" to R.string.btn_prev,
            "like" to R.string.btn_like,
            "next" to R.string.btn_next,
            "shuffle" to R.string.btn_shuffle,
            "repeat" to R.string.btn_repeat,
            "download" to R.string.btn_download,
            "toggle" to R.string.btn_play,
        )
        addColorEditor(buttonNames, colorPick) {
            colorPick = it
            renderPalettes()
        }

        // Цвет режимов: перемешать и цикл, у каждого состояния свой цвет
        palettesBox.addView(
            label(getString(R.string.color_modes_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        palettesBox.addView(label(getString(R.string.color_modes_hint), 13f, Palette.dim.toArgb()))
        val modeNames = listOf(
            "shuffle_on" to R.string.mode_shuffle_on,
            "shuffle_off" to R.string.mode_shuffle_off,
            "repeat_off" to R.string.mode_repeat_off,
            "repeat_all" to R.string.mode_repeat_all,
            "repeat_one" to R.string.mode_repeat_one,
        )
        addColorEditor(modeNames, modePick) {
            modePick = it
            renderPalettes()
        }

        // Полоска и время
        palettesBox.addView(
            label(getString(R.string.time_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        val remaining = WP.flag(this, WP.REMAINING, false)
        palettesBox.addView(chip(getString(R.string.time_remaining), remaining) {
            WP.setFlag(this, WP.REMAINING, !remaining)
            refreshWidgets()
            renderPalettes()
        })

        // Фон: прозрачность и своё фото 4 на 2
        palettesBox.addView(
            label(getString(R.string.widget_bg_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        palettesBox.addView(label(getString(R.string.bg_shared_hint), 13f, Palette.dim.toArgb()))
        // Размытие фона: для обложки и для своего фото. 0 — резко.
        if (ThemeStore.flag(this, ThemeStore.FLAG_BLUR) || Background.customOn(this)) {
            palettesBox.addView(
                slider({ getString(R.string.blur_value, it) }, 0, 100, Background.strength(this)) {
                    Background.setInt(this, Background.KEY_BLUR, it)
                    refreshWidgets()
                },
            )
        }

        val transOn = Background.transparencyOn(this)
        palettesBox.addView(chip(getString(R.string.trans_on), transOn) {
            Background.setBool(this, Background.KEY_TRANS_ON, !transOn)
            refreshWidgets()
            renderPalettes()
        })
        if (transOn) {
            palettesBox.addView(
                slider({ getString(R.string.trans_value, it) }, 0, 90, Background.transparency(this)) {
                    Background.setInt(this, Background.KEY_TRANS, it)
                    refreshWidgets()
                },
            )
        }

        val photoOn = Background.customOn(this)
        palettesBox.addView(chip(if (photoOn) getString(R.string.photo_on) else getString(R.string.photo_off), photoOn) {
            if (!Background.hasCustomFile(this)) {
                toastMsg(R.string.photo_none)
            } else {
                Background.setBool(this, Background.KEY_CUSTOM_ON, !photoOn)
                refreshWidgets()
                renderPalettes()
            }
        })
        palettesBox.addView(
            pillButton(getString(R.string.photo_pick)) { pickImage(reqBg) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )
        if (Background.hasCustomFile(this)) {
            val photoPreview = ImageView(this).apply {
                background = shape(Palette.cardArgb, dp(12))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(Background.layer(this@MainActivity, 720, 360, true))
            }
            palettesBox.addView(
                photoPreview,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)).apply { topMargin = dp(8) },
            )
            palettesBox.addView(slider({ getString(R.string.zoom_value, it) }, 100, 300, Background.zoom(this)) {
                Background.setInt(this, Background.KEY_ZOOM, it)
                photoPreview.setImageBitmap(Background.layer(this@MainActivity, 720, 360, true))
                refreshWidgets()
            })
            palettesBox.addView(slider({ getString(R.string.dx_value, it) }, -50, 50, Background.dx(this)) {
                Background.setInt(this, Background.KEY_DX, it)
                photoPreview.setImageBitmap(Background.layer(this@MainActivity, 720, 360, true))
                refreshWidgets()
            })
            palettesBox.addView(slider({ getString(R.string.dy_value, it) }, -50, 50, Background.dy(this)) {
                Background.setInt(this, Background.KEY_DY, it)
                photoPreview.setImageBitmap(Background.layer(this@MainActivity, 720, 360, true))
                refreshWidgets()
            })
            palettesBox.addView(
                pillButton(getString(R.string.photo_remove)) {
                    Background.removeCustom(this)
                    refreshWidgets()
                    renderPalettes()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
            )
        }

        palettesBox.addView(
            label(getString(R.string.preview_title), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(6)) },
        )
        val previewBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        palettesBox.addView(previewBox)

        palettesBox.addView(
            label(getString(R.string.theme_new), 16f, Palette.text.toArgb(), bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) },
        )
        fun field(hint: String, value: String) = EditText(this).apply {
            setText(value)
            this.hint = hint
            setTextColor(Palette.text.toArgb())
            setHintTextColor(Palette.dim.toArgb())
            setBackgroundColor(Palette.track.toArgb())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine()
        }
        val nameField = field(getString(R.string.theme_name_hint), "")
        val bgField = field("#RRGGBB", active.bg)
        val accentField = field("#RRGGBB", active.accent)
        val textField = field("#RRGGBB", active.text)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) }
        palettesBox.addView(nameField, lp)
        palettesBox.addView(label(getString(R.string.theme_bg), 13f, Palette.dim.toArgb()).apply { setPadding(0, dp(6), 0, 0) })
        palettesBox.addView(bgField, lp)
        palettesBox.addView(label(getString(R.string.theme_accent), 13f, Palette.dim.toArgb()).apply { setPadding(0, dp(6), 0, 0) })
        palettesBox.addView(accentField, lp)
        palettesBox.addView(label(getString(R.string.theme_text), 13f, Palette.dim.toArgb()).apply { setPadding(0, dp(6), 0, 0) })
        palettesBox.addView(textField, lp)
        palettesBox.addView(
            label(getString(R.string.theme_dot), 13f, Palette.dim.toArgb()).apply { setPadding(0, dp(6), 0, 0) },
        )
        val dotField = field(getString(R.string.theme_dot_hint), active.dot)
        palettesBox.addView(dotField, lp)

        fun updatePreview() {
            val bg = ThemeStore.normalize(bgField.text.toString())
            val accent = ThemeStore.normalize(accentField.text.toString())
            val fg = ThemeStore.normalize(textField.text.toString())
            val dotRaw = dotField.text.toString().trim()
            val dot = if (dotRaw.isEmpty()) fg else ThemeStore.normalize(dotRaw)
            if (bg != null && accent != null && fg != null && dot != null) {
                drawPreview(
                    previewBox,
                    android.graphics.Color.parseColor(bg),
                    android.graphics.Color.parseColor(accent),
                    android.graphics.Color.parseColor(fg),
                    android.graphics.Color.parseColor(dot),
                )
            }
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        listOf(bgField, accentField, textField).forEach { it.addTextChangedListener(watcher) }
        updatePreview()

        palettesBox.addView(
            pillButton(getString(R.string.theme_save)) {
                val name = nameField.text.toString().trim()
                val bg = ThemeStore.normalize(bgField.text.toString())
                val accent = ThemeStore.normalize(accentField.text.toString())
                val text = ThemeStore.normalize(textField.text.toString())
                val dotRaw = dotField.text.toString().trim()
                val dot = if (dotRaw.isEmpty()) "" else ThemeStore.normalize(dotRaw)
                when {
                    name.isEmpty() -> toastMsg(R.string.theme_bad_name)
                    bg == null || accent == null || text == null || dot == null -> toastMsg(R.string.theme_bad_hex)
                    else -> {
                        val err = ThemeStore.save(this, Pack(name, bg!!, accent!!, text!!, dot ?: ""))
                        when (err) {
                            "builtin" -> toastMsg(R.string.theme_builtin)
                            "name" -> toastMsg(R.string.theme_bad_name)
                            else -> {
                                ThemeStore.setActive(this, name)
                                CoroutineScope(Dispatchers.Main).launch {
                                    WidgetUpdater.refresh(applicationContext)
                                }
                                recreate()
                            }
                        }
                    }
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(14) },
        )

        palettesBox.addView(
            pillButton(getString(R.string.theme_copy)) {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("Lane pack", ThemeStore.encode(ThemeStore.active(this))))
                toastMsg(R.string.theme_copied)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) },
        )
        val importField = field(getString(R.string.theme_import_hint), "")
        palettesBox.addView(importField, lp)
        palettesBox.addView(
            pillButton(getString(R.string.theme_import)) {
                val p = ThemeStore.decode(importField.text.toString())
                if (p == null) {
                    toastMsg(R.string.theme_bad_import)
                } else {
                    when (ThemeStore.save(this, p)) {
                        "builtin" -> toastMsg(R.string.theme_builtin)
                        "name" -> toastMsg(R.string.theme_bad_name)
                        else -> {
                            ThemeStore.setActive(this, p.name)
                            refreshWidgets()
                            recreate()
                        }
                    }
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )
        makeCollapsible(palettesBox)
    }

    /**
     * Большие разделы (заголовки 20 sp) можно свернуть: нажатие на заголовок прячет содержимое.
     * Состояние каждого раздела запоминается.
     */
    private fun makeCollapsible(root: LinearLayout) {
        val prefs = getSharedPreferences("lane_widget", MODE_PRIVATE)
        val views = (0 until root.childCount).map { root.getChildAt(it) }
        root.removeAllViews()
        var body: LinearLayout? = null
        for (v in views) {
            if (v is TextView && (v.tag == "h1" || v.tag == "h2")) {
                val title = v.text.toString()
                val key = "fold_$title"
                val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                var collapsed = prefs.getBoolean(key, false)
                fun paint() {
                    v.text = (if (collapsed) "▸ " else "▾ ") + title
                    container.visibility = if (collapsed) View.GONE else View.VISIBLE
                }
                paint()
                v.setOnClickListener {
                    collapsed = !collapsed
                    prefs.edit().putBoolean(key, collapsed).apply()
                    paint()
                }
                root.addView(v)
                root.addView(container)
                body = container
            } else {
                val target = body
                if (target != null) target.addView(v) else root.addView(v)
            }
        }
    }

    /** Касание в предпросмотре переводим в доли слоя 720×272, учитывая, что картинка вписана по центру. */
    private fun touchToLayer(v: View, x: Float, y: Float): Pair<Float, Float> {
        val scale = minOf(v.width / 720f, v.height / 272f)
        val drawnW = 720f * scale
        val drawnH = 272f * scale
        val left = (v.width - drawnW) / 2f
        val top = (v.height - drawnH) / 2f
        return ((x - left) / drawnW).coerceIn(0f, 1f) to ((y - top) / drawnH).coerceIn(0f, 1f)
    }

    /** Ползунок с подписью. Значение применяется при отпускании. */
    private fun slider(title: (Int) -> String, lo: Int, hi: Int, value: Int, onStop: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val caption = label(title(value), 13f, Palette.dim.toArgb()).apply { setPadding(0, dp(10), 0, 0) }
        box.addView(caption)
        box.addView(
            SeekBar(this).apply {
                max = hi - lo
                progress = value - lo
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        caption.text = title(p + lo)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {
                        onStop(sb.progress + lo)
                    }
                })
            },
        )
        return box
    }

    private fun pickImage(requestCode: Int) {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
            requestCode,
        )
    }

    private fun createFile(requestCode: Int, name: String) {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, name),
            requestCode,
        )
    }

    private fun openJson(requestCode: Int) {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
            requestCode,
        )
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            reqBg -> if (Background.importFrom(this, uri)) {
                refreshWidgets()
                renderPalettes()
            } else {
                toastMsg(R.string.bg_bad)
            }
            reqExport -> toastMsg(
                if (SettingsIO.export(this, uri)) R.string.settings_exported else R.string.settings_failed,
            )
            reqImport -> if (SettingsIO.import(this, uri)) {
                refreshWidgets()
                recreate()
            } else {
                toastMsg(R.string.settings_failed)
            }
        }
    }

    /** Выбор кнопки или режима из списка, поле #RRGGBB, образцы, применить и вернуть по умолчанию. */
    private fun addColorEditor(names: List<Pair<String, Int>>, picked: String, onPick: (String) -> Unit) {
        for ((name, res) in names) {
            palettesBox.addView(chip(getString(res), name == picked) { onPick(name) })
        }
        val hex = EditText(this).apply {
            setText(WP.color(this@MainActivity, picked)?.let { String.format("#%06X", it and 0xFFFFFF) } ?: "")
            hint = "#RRGGBB"
            setTextColor(Palette.text.toArgb())
            setHintTextColor(Palette.dim.toArgb())
            setBackgroundColor(Palette.track.toArgb())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine()
        }
        palettesBox.addView(hex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (h in listOf("#FFFFFF", "#000000", "#F4A9C0", "#7FD1AE", "#8AB4F8", "#FFD166", "#FF7A7A")) {
            swatches.addView(
                View(this).apply {
                    background = shape(android.graphics.Color.parseColor(h), dp(14))
                    setOnClickListener { hex.setText(h) }
                },
                LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) },
            )
        }
        palettesBox.addView(swatches, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        palettesBox.addView(
            pillButton(getString(R.string.btn_color_apply)) {
                val n = ThemeStore.normalize(hex.text.toString())
                if (n == null) {
                    toastMsg(R.string.theme_bad_hex)
                } else {
                    WP.setColor(this, picked, n)
                    refreshWidgets()
                    renderPalettes()
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )
        palettesBox.addView(
            pillButton(getString(R.string.btn_color_reset)) {
                WP.setColor(this, picked, null)
                refreshWidgets()
                renderPalettes()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) },
        )
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_message)
            .setPositiveButton(R.string.reset_button) { _, _ -> resetAll() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetAll() {
        getSharedPreferences("lane_widget", MODE_PRIVATE).edit().clear().commit()
        Background.removeCustom(this)
        refreshWidgets()
        toastMsg(R.string.reset_done)
        recreate()
    }

    private fun refreshWidgets() {
        CoroutineScope(Dispatchers.Main).launch { WidgetUpdater.refresh(applicationContext) }
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    /** Макет карточки виджета в заданных цветах. Перерисовывается при каждом изменении поля. */
    private fun drawPreview(box: LinearLayout, bg: Int, accent: Int, fg: Int, dot: Int) {
        box.removeAllViews()
        val track = ColorUtils.blendARGB(bg, fg, 0.2f)
        val dim = ColorUtils.blendARGB(fg, bg, 0.45f)
        val coverTone = ColorUtils.blendARGB(bg, fg, 0.07f)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.55) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        val match = LinearLayout.LayoutParams.MATCH_PARENT

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(bg, dp(26))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(View(this).apply { background = shape(coverTone, dp(18)) }, LinearLayout.LayoutParams(dp(92), dp(92)))
        left.addView(
            View(this).apply { background = shape(accent, dp(5)) },
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { topMargin = dp(4) },
        )
        card.addView(left)

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        right.addView(TextView(this).apply {
            this.text = "Название трека"
            setTextColor(fg)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
        })
        right.addView(TextView(this).apply {
            this.text = "Исполнитель"
            setTextColor(accent)
            textSize = 13f
        })

        // Одна линия: заливка, точка на границе позиции, остаток трека.
        val barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        barRow.addView(View(this).apply { background = shape(accent, dp(2)) }, LinearLayout.LayoutParams(0, dp(4), 0.4f))
        barRow.addView(View(this).apply { background = shape(dot, dp(4)) }, LinearLayout.LayoutParams(dp(8), dp(8)))
        barRow.addView(View(this).apply { background = shape(track, dp(2)) }, LinearLayout.LayoutParams(0, dp(4), 0.6f))
        right.addView(barRow, LinearLayout.LayoutParams(match, dp(8)).apply { topMargin = dp(10) })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(TextView(this).apply { this.text = "0:55"; setTextColor(dim); textSize = 11f })
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(
            TextView(this).apply {
                this.text = "▶"
                setTextColor(onAccent)
                textSize = 14f
                gravity = Gravity.CENTER
                background = shape(accent, dp(20))
            },
            LinearLayout.LayoutParams(dp(40), dp(40)),
        )
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(TextView(this).apply { this.text = "3:36"; setTextColor(dim); textSize = 11f })
        right.addView(controls, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(10) })

        card.addView(right, LinearLayout.LayoutParams(0, wrap, 1f))
        box.addView(card, LinearLayout.LayoutParams(match, wrap))
    }

    private fun toastMsg(res: Int) = android.widget.Toast.makeText(this, res, android.widget.Toast.LENGTH_LONG).show()

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
        for (c in list.filter { !it.packageName.contains("youtube") }.distinctBy { it.packageName }) {
            val hidden = Blocklist.isBlocked(this, c.packageName)
            val state = getString(if (hidden) R.string.player_hidden else R.string.player_shown)
            playersBox.addView(chip("${appLabel(c.packageName)}: $state", !hidden) {
                Blocklist.setBlocked(this, c.packageName, !hidden)
                MediaListener.instance?.reattach()
                CoroutineScope(Dispatchers.Main).launch { WidgetUpdater.refresh(applicationContext) }
                renderPlayers()
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

    private val headerTag = "header"
    private val reqBg = 4711
    private var colorPick = "prev"
    private var modePick = "shuffle_on"
    private val reqExport = 4712
    private val reqImport = 4713

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        tag = when {
            sp == 20f -> "h1"
            sp == 16f && bold -> "h2"
            else -> null
        }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
