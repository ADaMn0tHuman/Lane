package app.lanewidget

import android.app.Activity
import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Невидимая «прослойка» для нажатия на виджет. Просит у плеера экран, который он сам считает
 * главным для текущей сессии (обычно «Сейчас играет»).
 *
 * На Android 14 отправка чужого PendingIntent без явного разрешения молча блокируется,
 * поэтому мы разрешаем запуск из нашего (видимого) приложения. Если через 0,6 секунды
 * выяснилось, что экран плеера так и не открылся, запускаем само приложение плеера.
 */
class OpenPlayerActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var left = false
    private var how = ""
    private var pkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val c = MediaRepo.pick(this, MediaRepo.controllers(this))
            pkg = c?.packageName
            val pi = c?.sessionActivity
            if (pi == null) {
                how = "у плеера нет экрана сессии, открываю приложение"
                openLauncher()
                return
            }
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            pi.send(this, 0, null, null, null, null, options)
            how = "экран сессии отправлен"
            handler.postDelayed({ checkOpened() }, 600)
        } catch (e: Exception) {
            how = "ошибка: ${e.javaClass.simpleName}: ${e.message}, открываю приложение"
            openLauncher()
        }
    }

    override fun onPause() {
        super.onPause()
        left = true
    }

    private fun checkOpened() {
        // Если экран плеера открылся, наше окно потеряло фокус.
        if (left || !hasWindowFocus()) {
            record("$how, открылся")
            finish()
        } else {
            how += ", но экран не открылся, открываю приложение"
            openLauncher()
        }
    }

    private fun openLauncher() {
        try {
            pkg?.let { p -> packageManager.getLaunchIntentForPackage(p)?.let { startActivity(it) } }
        } catch (_: Exception) {
        }
        record(how)
        finish()
    }

    private fun record(text: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lastResult = "$t: $text"
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        /** Что случилось при последнем нажатии на виджет. Показывается на экране диагностики. */
        @Volatile var lastResult: String = "ещё не нажимали"
    }
}
