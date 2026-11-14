package app.lanewidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Своё уведомление с музыкой: обложка, название, полоска, кнопки.
 * Кнопки работают на экране блокировки, если уведомление там разрешено.
 * Обновляется вместе с виджетом (см. WidgetUpdater).
 */
object NotifyPlayer {

    const val ID = 3001
    private const val CHANNEL = "player"
    const val EXTRA = "cmd"

    const val ON = "notify_on"
    const val COVER = "notify_cover"
    const val BAR = "notify_bar"
    const val SKIP = "notify_skip"
    const val LOCK = "notify_lock"
    const val BG = "notify_bg"
    const val FG = "notify_fg"
    const val ACC = "notify_accent"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lane_widget", Context.MODE_PRIVATE)

    fun flag(ctx: Context, key: String, default: Boolean): Boolean = prefs(ctx).getBoolean(key, default)

    fun setFlag(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    fun color(ctx: Context, key: String): Int? {
        val hex = prefs(ctx).getString(key, null) ?: return null
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (e: Exception) {
            null
        }
    }

    fun setColor(ctx: Context, key: String, hex: String?) {
        val e = prefs(ctx).edit()
        if (hex == null) e.remove(key) else e.putString(key, hex)
        e.apply()
    }

    /** Показывает или скрывает уведомление в зависимости от настроек и состояния плеера. */
    suspend fun update(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!flag(ctx, ON, false)) {
            nm.cancel(ID)
            return
        }
        val s = MediaRepo.read(ctx)
        if (!s.hasAccess || s.title.isBlank()) {
            nm.cancel(ID)
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_LOW),
        )
        nm.notify(ID, build(ctx, s))
    }

    private fun build(ctx: Context, s: MediaState): Notification {
        val bg = color(ctx, BG) ?: Palette.cardArgb
        val fg = color(ctx, FG) ?: Palette.text.toArgb()
        val acc = color(ctx, ACC) ?: Palette.pink.toArgb()

        val rv = RemoteViews(ctx.packageName, R.layout.notif_player)
        rv.setInt(R.id.notif_root, "setBackgroundColor", bg)
        rv.setTextViewText(R.id.notif_title, s.title)
        rv.setTextColor(R.id.notif_title, fg)
        rv.setTextViewText(R.id.notif_artist, s.artist)
        rv.setTextColor(R.id.notif_artist, acc)

        val showCover = flag(ctx, COVER, true)
        rv.setViewVisibility(R.id.notif_cover, if (showCover) View.VISIBLE else View.GONE)
        if (showCover) {
            val cover = s.cover
            if (cover != null) {
                rv.setImageViewBitmap(R.id.notif_cover, cover)
            } else {
                rv.setImageViewResource(R.id.notif_cover, R.drawable.ic_music)
            }
        }

        val showBar = flag(ctx, BAR, true)
        rv.setViewVisibility(R.id.notif_bar, if (showBar) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.notif_time, if (showBar) View.VISIBLE else View.GONE)
        if (showBar) {
            rv.setImageViewBitmap(R.id.notif_bar, ProgressBitmap.draw(s.progress))
            val total = if (s.durationMs > 0) MediaRepo.formatTime(s.durationMs) else "--:--"
            rv.setTextViewText(R.id.notif_time, "${MediaRepo.formatTime(s.positionMs)} / $total")
            rv.setTextColor(R.id.notif_time, fg)
        }

        val showSkip = flag(ctx, SKIP, true)
        rv.setViewVisibility(R.id.notif_prev, if (showSkip) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.notif_next, if (showSkip) View.VISIBLE else View.GONE)
        rv.setInt(R.id.notif_prev, "setColorFilter", fg)
        rv.setInt(R.id.notif_next, "setColorFilter", fg)
        rv.setInt(R.id.notif_toggle, "setColorFilter", acc)
        rv.setImageViewResource(R.id.notif_toggle, if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        rv.setOnClickPendingIntent(R.id.notif_prev, action(ctx, "prev", 1))
        rv.setOnClickPendingIntent(R.id.notif_toggle, action(ctx, "toggle", 2))
        rv.setOnClickPendingIntent(R.id.notif_next, action(ctx, "next", 3))

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_music)
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(open)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(
                if (flag(ctx, LOCK, true)) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET,
            )
            .build()
    }

    private fun action(ctx: Context, cmd: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            code,
            Intent(ctx, NotifyActionReceiver::class.java).putExtra(EXTRA, cmd),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Нажатия кнопок уведомления: назад, играть/пауза, вперёд. */
class NotifyActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val cmd = intent.getStringExtra(NotifyPlayer.EXTRA) ?: return
        MediaRepo.rebind(ctx)
        val c = MediaRepo.pick(ctx, MediaRepo.controllers(ctx)) ?: return
        val t = c.transportControls
        when (cmd) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            "toggle" -> {
                val willPlay = !MediaRepo.isPlayingState(c.playbackState?.state)
                MediaRepo.forcePlaying(willPlay)
                if (willPlay) t.play() else t.pause()
            }
        }
        CoroutineScope(Dispatchers.Main).launch { WidgetUpdater.refresh(ctx.applicationContext) }
    }
}
