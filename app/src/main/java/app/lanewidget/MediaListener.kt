package app.lanewidget

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Нужен только ради доступа к медиасессиям: пока пользователь выдал доступ к уведомлениям,
 * система разрешает читать трек и управлять плеером. Сервис следит за сменой трека и
 * обновляет виджет, а пока музыка играет, подвигает полосу прогресса раз в 15 секунд.
 */
class MediaListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tick: Job? = null
    private var attached: List<MediaController> = emptyList()
    private var manager: MediaSessionManager? = null

    private val component by lazy { ComponentName(this, MediaListener::class.java) }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
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
        scope.launch { LaneWidget().updateAll(applicationContext) }
    }

    override fun onDestroy() {
        release()
        scope.cancel()
        super.onDestroy()
    }

    private fun release() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        attached.forEach { runCatching { it.unregisterCallback(callback) } }
        attached = emptyList()
        tick?.cancel()
        tick = null
    }

    private fun attach(list: List<MediaController>) {
        attached.forEach { runCatching { it.unregisterCallback(callback) } }
        attached = list
        list.forEach { it.registerCallback(callback) }
        refresh()
    }

    private fun refresh() {
        scope.launch {
            LaneWidget().updateAll(applicationContext)
            val playing = MediaRepo.pick(attached)?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) startTick() else {
                tick?.cancel()
                tick = null
            }
        }
    }

    private fun startTick() {
        if (tick?.isActive == true) return
        tick = scope.launch {
            while (isActive) {
                delay(15_000)
                LaneWidget().updateAll(applicationContext)
            }
        }
    }
}
