package app.lanewidget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

data class MediaState(
    val title: String,
    val artist: String,
    val cover: Bitmap?,
    val playing: Boolean,
    val progress: Float,
    val packageName: String?,
    val hasAccess: Boolean,
    val flash: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

object MediaRepo {

    private const val PREFS = "lane_widget"
    private const val KEY_LAST = "last_pkg"
    private const val KEY_LIKE = "like_"

    private val LIKE_WORDS = listOf("like", "love", "heart", "fav", "save", "нрав", "люб", "лайк", "избран", "сохран")
    private val DISLIKE_WORDS = listOf("dislike", "не нрав", "дизлайк")
    private val UNDO_WORDS = listOf("unlike", "unfav", "unsave", "remove", "delete", "удал", "убрать", "отменить")

    // Мгновенная реакция кнопок: иконка меняется сразу, не дожидаясь ответа плеера.
    @Volatile private var forcedPlaying: Boolean? = null
    @Volatile private var forcedPlayingUntil = 0L
    @Volatile private var likedUntil = 0L

    // Кэш обложки, загруженной по ссылке.
    private var coverKey: String? = null
    private var coverBitmap: Bitmap? = null

    // Кэш уменьшенной обложки, пришедшей картинкой. Без него при обновлении раз в секунду
    // мы бы каждый раз заново создавали уменьшенную копию.
    private var rawCover: Bitmap? = null
    private var rawCoverScaled: Bitmap? = null

    fun formatTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
        else String.format(java.util.Locale.ROOT, "%d:%02d", m, sec)
    }

    fun forcePlaying(value: Boolean) {
        forcedPlaying = value
        forcedPlayingUntil = SystemClock.elapsedRealtime() + 3000
    }

    /**
     * «Плеер сейчас играет или вот-вот заиграет». Только STATE_PLAYING мало: при смене трека
     * и подгрузке потока многие плееры на время сообщают BUFFERING / CONNECTING / SKIPPING_*,
     * и иконка залипала на «треугольнике». Так же считает и системная шторка Android.
     */
    fun isPlayingState(state: Int?): Boolean = when (state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    fun flashLiked() {
        likedUntil = SystemClock.elapsedRealtime() + 2200
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markActive(ctx: Context, pkg: String) {
        prefs(ctx).edit().putString(KEY_LAST, pkg).apply()
    }

    fun savedLike(ctx: Context, pkg: String): String? = prefs(ctx).getString(KEY_LIKE + pkg, null)

    fun saveLike(ctx: Context, pkg: String, action: String) {
        prefs(ctx).edit().putString(KEY_LIKE + pkg, action).apply()
    }

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Просит систему заново привязать сервис, если его усыпили. */
    fun rebind(ctx: Context) {
        if (!hasAccess(ctx)) return
        val cn = ComponentName(ctx, MediaListener::class.java)
        runCatching { NotificationListenerService.requestRebind(cn) }
        if (!MediaListener.connected) {
            // Известный приём: выключить и включить компонент, тогда система привяжет сервис заново.
            runCatching {
                val pm = ctx.packageManager
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }

    /** Активные плееры. Видеосервисы и браузеры (см. Blocklist) отбрасываются, если не просили иначе. */
    fun controllers(ctx: Context, includeBlocked: Boolean = false): List<MediaController> {
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return emptyList()
        val all = try {
            msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        } catch (e: SecurityException) {
            emptyList()
        }
        return if (includeBlocked) all else all.filter { !Blocklist.isBlocked(ctx, it.packageName) }
    }

    /**
     * Какой плеер показывать. Если играет один, берём его. Если играют несколько
     * или не играет никто, берём тот, который последним начинал играть.
     */
    fun pick(ctx: Context, list: List<MediaController>): MediaController? {
        val last = prefs(ctx).getString(KEY_LAST, null)
        val playing = list.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playing.isNotEmpty()) {
            return playing.firstOrNull { it.packageName == last } ?: playing.first()
        }
        val starting = list.filter { isPlayingState(it.playbackState?.state) }
        if (starting.isNotEmpty()) {
            return starting.firstOrNull { it.packageName == last } ?: starting.first()
        }
        return list.firstOrNull { it.packageName == last && it.metadata != null }
            ?: list.firstOrNull { it.metadata != null }
    }

    // ---- «Нравится» через дополнительные действия плеера ----

    private fun actionText(a: PlaybackState.CustomAction) = "${a.action} ${a.name}".lowercase()

    fun findLikeAction(ctx: Context, c: MediaController): PlaybackState.CustomAction? {
        val actions = c.playbackState?.customActions.orEmpty()
        savedLike(ctx, c.packageName)?.let { saved ->
            actions.firstOrNull { it.action == saved }?.let { return it }
        }
        return actions.firstOrNull { a ->
            val s = actionText(a)
            LIKE_WORDS.any { it in s } && DISLIKE_WORDS.none { it in s } && UNDO_WORDS.none { it in s }
        }
    }

    fun alreadyLiked(c: MediaController): Boolean =
        c.playbackState?.customActions.orEmpty().any { a ->
            val s = actionText(a)
            UNDO_WORDS.any { it in s } && DISLIKE_WORDS.none { it in s }
        }

    // ---- Чтение состояния для виджета ----

    suspend fun read(ctx: Context): MediaState {
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

        val c = pick(ctx, controllers(ctx)) ?: return idle
        val meta = c.metadata ?: return idle
        val state = c.playbackState
        val now = SystemClock.elapsedRealtime()

        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return idle
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val realPlaying = state?.state == PlaybackState.STATE_PLAYING
        val intentPlaying = isPlayingState(state?.state)
        val forced = forcedPlaying
        val playing = when {
            // Пока плеер не подтвердил нажатие, показываем то, чего хотел пользователь.
            forced != null && now < forcedPlayingUntil && intentPlaying != forced -> forced
            else -> {
                if (forced != null) forcedPlaying = null
                intentPlaying
            }
        }

        val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        var position = (state?.position ?: 0L).coerceAtLeast(0L)
        if (realPlaying && state != null && state.lastPositionUpdateTime > 0) {
            position += ((now - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        if (duration > 0) position = position.coerceAtMost(duration)
        val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

        return MediaState(
            title = title,
            artist = artist,
            cover = loadCover(ctx, meta),
            playing = playing,
            progress = progress,
            packageName = c.packageName,
            hasAccess = true,
            flash = now < likedUntil,
            positionMs = position,
            durationMs = duration,
        )
    }

    private suspend fun loadCover(ctx: Context, meta: MediaMetadata): Bitmap? {
        val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) {
            if (bmp === rawCover) return rawCoverScaled
            val scaled = scale(bmp)
            rawCover = bmp
            rawCoverScaled = scaled
            return scaled
        }

        val uri = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: return null

        if (uri == coverKey) return coverBitmap
        val loaded = withContext(Dispatchers.IO) { fetch(ctx, uri) }
        if (loaded != null) {
            coverKey = uri
            coverBitmap = loaded
        }
        return loaded
    }

    private fun fetch(ctx: Context, uriStr: String): Bitmap? = runCatching {
        val parsed = Uri.parse(uriStr)
        val stream = when (parsed.scheme) {
            "http", "https" -> {
                val url = if (uriStr.startsWith("http://")) "https://" + uriStr.removePrefix("http://") else uriStr
                URL(url).openConnection().apply {
                    connectTimeout = 3000
                    readTimeout = 4000
                }.getInputStream()
            }
            else -> ctx.contentResolver.openInputStream(parsed)
        }
        stream?.use { decode(it.readBytes()) }
    }.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 320 && bounds.outHeight / (sample * 2) >= 320) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { scale(it) }
    }

    private fun scale(b: Bitmap, max: Int = 320): Bitmap {
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
