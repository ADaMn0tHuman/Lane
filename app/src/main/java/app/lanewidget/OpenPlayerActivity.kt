package app.lanewidget

import android.app.Activity
import android.os.Bundle

/**
 * Невидимая «прослойка» для нажатия на виджет. Просит у плеера экран, который он сам считает
 * главным для текущей сессии (обычно это экран «Сейчас играет»), а если плеер его не дал,
 * просто открывает приложение плеера.
 */
class OpenPlayerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val c = MediaRepo.pick(this, MediaRepo.controllers(this))
            val sent = c?.sessionActivity?.let { pi -> runCatching { pi.send() }.isSuccess } == true
            if (!sent && c != null) {
                packageManager.getLaunchIntentForPackage(c.packageName)?.let { startActivity(it) }
            }
        } catch (_: Exception) {
        } finally {
            finish()
        }
    }
}
