package app.lanewidget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat

data class MediaState(
    val title: String,
    val artist: String,
    val cover: Bitmap?,
    val playing: Boolean,
    val progress: Float,
    val packageName: String?,
    val hasAccess: Boolean,
)

object MediaRepo {

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun controllers(ctx: Context): List<MediaController> {
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return emptyList()
        return try {
            msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Сначала играющий плеер, иначе первый, у которого есть метаданные. */
    fun pick(list: List<MediaController>): MediaController? =
        list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null }

    fun read(ctx: Context): MediaState {
        if (!hasAccess(ctx)) {
            return MediaState(
                ctx.getString(R.string.need_access),
                ctx.getString(R.string.tap_to_grant),
                null, false, 0f, null, false,
            )
        }

        val idle = MediaState(
            ctx.getString(R.string.idle_title),
            ctx.getString(R.string.idle_artist),
            null, false, 0f, null, true,
        )

        val c = pick(controllers(ctx)) ?: return idle
        val meta = c.metadata ?: return idle
        val state = c.playbackState

        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return idle
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val playing = state?.state == PlaybackState.STATE_PLAYING
        val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
        var position = state?.position ?: 0L
        if (playing && state != null && state.lastPositionUpdateTime > 0) {
            position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

        return MediaState(title, artist, art?.let { scale(it) }, playing, progress, c.packageName, true)
    }

    private fun scale(b: Bitmap, max: Int = 400): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= max) return b
        val k = max.toFloat() / m
        return Bitmap.createScaledBitmap(
            b,
            (b.width * k).toInt().coerceAtLeast(1),
            (b.height * k).toInt().coerceAtLeast(1),
            true,
        )
    }
}
