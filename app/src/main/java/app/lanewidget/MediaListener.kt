package app.lanewidget

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
 * 3) таймер раз в 10 секунд, пока играет музыка.
 */
class MediaListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tick: Job? = null
    private var pending: Job? = null
    private var manager: MediaSessionManager? = null
    private val bound = mutableListOf<Pair<MediaController, Cb>>()

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
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        manager = m
        m.addOnActiveSessionsChangedListener(sessionsListener, component)
        attach(runCatching { m.getActiveSessions(component) }.getOrDefault(emptyList()))
    }

    override fun onListenerDisconnected() {
        release()
        scope.launch { WidgetUpdater.refresh(applicationContext) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (isMedia(sbn)) refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (isMedia(sbn)) refresh()
    }

    override fun onDestroy() {
        release()
        scope.cancel()
        super.onDestroy()
    }

    private fun isMedia(sbn: StatusBarNotification?): Boolean =
        sbn?.notification?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true

    private fun release() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        bound.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        bound.clear()
        pending?.cancel()
        tick?.cancel()
        tick = null
    }

    private fun attach(list: List<MediaController>) {
        bound.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        bound.clear()
        list.forEach { c ->
            val cb = Cb(c.packageName)
            runCatching { c.registerCallback(cb) }
            bound.add(c to cb)
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
        if (!playing) {
            tick?.cancel()
            tick = null
            return
        }
        if (tick?.isActive == true) return
        tick = scope.launch {
            while (isActive) {
                delay(10_000)
                WidgetUpdater.refresh(applicationContext)
            }
        }
    }
}
