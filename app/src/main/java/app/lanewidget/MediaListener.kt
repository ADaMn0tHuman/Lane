package app.lanewidget

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.media.session.MediaControllerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Следит за плеерами и обновляет виджет. Обновление приходит из трёх источников,
 * чтобы не зависеть от капризов одного из них:
 * 1) колбэки медиасессий (смена трека, пауза/играть),
 * 2) медиауведомления плееров,
 * 3) таймер раз в секунду, пока играет музыка и экран включён (двигает полоску и время).
 *
 * Видеосервисы и браузеры (см. Blocklist) игнорируются целиком.
 */
class MediaListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tick: Job? = null
    private var pending: Job? = null
    private var manager: MediaSessionManager? = null
    private var screenReceiver: BroadcastReceiver? = null
    private val bound = mutableListOf<Pair<MediaController, Cb>>()
    private val boundCompat = mutableListOf<Pair<MediaControllerCompat, MediaControllerCompat.Callback>>()

    private val component by lazy { ComponentName(this, MediaListener::class.java) }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list.orEmpty()) }

    private inner class Cb(private val pkg: String) : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (MediaRepo.isPlayingState(state?.state)) {
                MediaRepo.markActive(applicationContext, pkg)
            }
            refresh()
        }

        override fun onSessionDestroyed() = refresh()
    }

    override fun onListenerConnected() {
        connected = true
        instance = this
        KeepAliveService.ensure(applicationContext)
        registerScreenReceiver()
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        m.addOnActiveSessionsChangedListener(sessionsListener, component)
        reattach()
    }

    override fun onListenerDisconnected() {
        connected = false
        instance = null
        release()
        scope.launch { WidgetUpdater.refresh(applicationContext) }
        // Система отвязала сервис (например, при нехватке памяти): сразу просим привязать обратно.
        runCatching { NotificationListenerService.requestRebind(component) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (isMedia(sbn)) refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (isMedia(sbn)) refresh()
    }

    override fun onDestroy() {
        connected = false
        instance = null
        release()
        KeepAliveService.stop(applicationContext)
        scope.cancel()
        super.onDestroy()
    }

    /** Перечитать список плееров заново. Нужно, когда пользователь скрыл или вернул плеер. */
    fun reattach() {
        val m = manager ?: return
        attach(runCatching { m.getActiveSessions(component) }.getOrDefault(emptyList()))
    }

    private fun isMedia(sbn: StatusBarNotification?): Boolean =
        sbn != null &&
            sbn.notification?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true &&
            !Blocklist.isBlocked(this, sbn.packageName)

    private fun interactive(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Экран включился: сразу показываем актуальное время. Выключился: останавливаем таймер.
                if (intent.action == Intent.ACTION_SCREEN_ON) refresh() else updateTick()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching {
            ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiver = r
        }
    }

    private fun release() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        bound.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        bound.clear()
        boundCompat.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        boundCompat.clear()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        pending?.cancel()
        tick?.cancel()
        tick = null
    }

    private fun attach(raw: List<MediaController>) {
        val list = raw.filter { !Blocklist.isBlocked(this, it.packageName) }
        bound.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        bound.clear()
        boundCompat.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        boundCompat.clear()
        list.forEach { c ->
            val cb = Cb(c.packageName)
            runCatching { c.registerCallback(cb) }
            bound.add(c to cb)

            // Смена перемешивания и повтора не вызывает обычных колбэков, ловим отдельно.
            PlayerControls.compat(applicationContext, c)?.let { cc ->
                val ccb = object : MediaControllerCompat.Callback() {
                    override fun onShuffleModeChanged(shuffleMode: Int) = refresh()
                    override fun onRepeatModeChanged(repeatMode: Int) = refresh()
                }
                runCatching { cc.registerCallback(ccb) }
                boundCompat.add(cc to ccb)
            }
        }
        val playing = list.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playing.size == 1) MediaRepo.markActive(applicationContext, playing.first().packageName)
        refresh()
    }

    private fun refresh() {
        pending?.cancel()
        pending = scope.launch {
            delay(50)
            // Саму отрисовку запускаем отдельно, чтобы новое событие не отменяло её на полпути.
            scope.launch { WidgetUpdater.refresh(applicationContext) }
            updateTick()
        }
    }

    private fun updateTick() {
        val c = MediaRepo.pick(this, MediaRepo.controllers(this))
        val playing = MediaRepo.isPlayingState(c?.playbackState?.state)
        if (!playing || !interactive()) {
            tick?.cancel()
            tick = null
            return
        }
        if (tick?.isActive == true) return
        tick = scope.launch {
            while (isActive) {
                delay(if (WP.flag(applicationContext, WP.SLOW, false)) 2000L else TICK_MS)
                // Экран погас: таймер не нужен. При включении экрана его запустит приёмник выше.
                if (!interactive()) break
                WidgetUpdater.refresh(applicationContext)
            }
        }
    }

    companion object {
        /** Как часто двигается полоска и время. Меньше значение: плавнее, но больше расход батареи. */
        private const val TICK_MS = 1000L

        @Volatile var connected = false
        @Volatile var instance: MediaListener? = null
    }
}
