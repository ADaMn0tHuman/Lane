package app.lanewidget

import android.content.Context
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

/**
 * Дополнительные кнопки виджета: «нравится», перемешать, повтор, скачать.
 *
 * Как это работает:
 * - shuffle и repeat умеет только MediaControllerCompat (в обычном MediaController их нет),
 *   поэтому берём его. Если плеер их не отдаёт, пробуем найти подходящее «дополнительное
 *   действие» плеера по названию.
 * - «нравится» и «скачать» у плееров нет стандартной команды, поэтому ищем дополнительное
 *   действие по словам в названии. «Нравится» ещё умеет работать через сердечко-рейтинг.
 */
object PlayerControls {

    data class Info(
        val liked: Boolean = false,
        val shuffleSupported: Boolean = false,
        val shuffleOn: Boolean = false,
        val repeatSupported: Boolean = false,
        val repeatMode: Int = 0,
        val downloadSupported: Boolean = false,
        val downloaded: Boolean = false,
    )

    private val SHUFFLE_WORDS = listOf("shuffle", "перемеш", "random", "случайн")
    private val REPEAT_WORDS = listOf("repeat", "loop", "повтор", "цикл")
    private val OFF_PHRASES = listOf("turn off", "disable", "выключ", "отключ", "unshuffle")
    private val DOWNLOADED_WORDS = listOf("downloaded", "скачан", "загружен")

    private var compatToken: Any? = null
    private var compatCtl: MediaControllerCompat? = null

    private fun compat(ctx: Context, c: MediaController): MediaControllerCompat? = try {
        val t = c.sessionToken
        if (t != compatToken || compatCtl == null) {
            compatCtl = MediaControllerCompat(ctx.applicationContext, MediaSessionCompat.Token.fromToken(t))
            compatToken = t
        }
        compatCtl
    } catch (e: Exception) {
        null
    }

    private fun actions(c: MediaController) = c.playbackState?.customActions.orEmpty()

    private fun actionWith(c: MediaController, words: List<String>): PlaybackState.CustomAction? =
        actions(c).firstOrNull { a -> MediaRepo.actionText(a).let { s -> words.any { it in s } } }

    private fun heartRating(c: MediaController): Rating? =
        c.metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)
            ?.takeIf { it.ratingStyle == Rating.RATING_HEART }

    private fun shuffleMode(cc: MediaControllerCompat?): Int =
        cc?.let { runCatching { it.shuffleMode }.getOrDefault(-1) } ?: -1

    private fun repeatMode(cc: MediaControllerCompat?): Int =
        cc?.let { runCatching { it.repeatMode }.getOrDefault(-1) } ?: -1

    fun info(ctx: Context, c: MediaController): Info {
        val cc = compat(ctx, c)

        val sMode = shuffleMode(cc)
        val sAct = actionWith(c, SHUFFLE_WORDS)
        val shuffleSupported = sMode != -1 || sAct != null
        val shuffleOn = if (sMode != -1) sMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
        else sAct?.let { a -> MediaRepo.actionText(a).let { s -> OFF_PHRASES.any { it in s } } } == true

        val rMode = repeatMode(cc)
        val rAct = actionWith(c, REPEAT_WORDS)
        val repeatSupported = rMode != -1 || rAct != null
        val repeat = when {
            rMode == PlaybackStateCompat.REPEAT_MODE_ONE -> 1
            rMode == PlaybackStateCompat.REPEAT_MODE_ALL || rMode == PlaybackStateCompat.REPEAT_MODE_GROUP -> 2
            rMode != -1 -> 0
            rAct != null && MediaRepo.actionText(rAct).let { s -> OFF_PHRASES.any { it in s } } -> 2
            else -> 0
        }

        val dAct = actionWith(c, MediaRepo.DOWNLOAD_WORDS)
        val downloaded = dAct?.let { a ->
            MediaRepo.actionText(a).let { s ->
                DOWNLOADED_WORDS.any { it in s } || MediaRepo.UNDO_WORDS.any { it in s }
            }
        } == true

        val liked = MediaRepo.isLikedByActions(c) || heartRating(c)?.hasHeart() == true

        return Info(
            liked = liked,
            shuffleSupported = shuffleSupported,
            shuffleOn = shuffleOn,
            repeatSupported = repeatSupported,
            repeatMode = repeat,
            downloadSupported = dAct != null,
            downloaded = downloaded,
        )
    }

    // Все функции ниже возвращают текст для всплывающей подсказки, если команду выполнить не удалось.

    fun toggleLike(ctx: Context, c: MediaController): String? {
        val liked = MediaRepo.effectiveLiked(info(ctx, c).liked)
        val t = c.transportControls
        if (liked) {
            val undo = MediaRepo.findUnlikeAction(c)
            if (undo != null) {
                t.sendCustomAction(undo, undo.extras)
                MediaRepo.forceLiked(false)
                return null
            }
            if (heartRating(c) != null) {
                t.setRating(Rating.newHeartRating(false))
                MediaRepo.forceLiked(false)
                return null
            }
            return ctx.getString(R.string.cant_unlike)
        }
        val like = MediaRepo.findLikeAction(ctx, c)
        if (like != null) {
            t.sendCustomAction(like, like.extras)
            MediaRepo.forceLiked(true)
            return null
        }
        if (heartRating(c) != null) {
            t.setRating(Rating.newHeartRating(true))
            MediaRepo.forceLiked(true)
            return null
        }
        return ctx.getString(R.string.no_like_action)
    }

    fun toggleShuffle(ctx: Context, c: MediaController): String? {
        val cc = compat(ctx, c)
        val mode = shuffleMode(cc)
        if (mode != -1 && cc != null) {
            cc.transportControls.setShuffleMode(
                if (mode == PlaybackStateCompat.SHUFFLE_MODE_NONE) PlaybackStateCompat.SHUFFLE_MODE_ALL
                else PlaybackStateCompat.SHUFFLE_MODE_NONE,
            )
            return null
        }
        val act = actionWith(c, SHUFFLE_WORDS) ?: return ctx.getString(R.string.no_shuffle)
        c.transportControls.sendCustomAction(act, act.extras)
        return null
    }

    /** Повтор по кругу: выключен, весь список, один трек, снова выключен. */
    fun cycleRepeat(ctx: Context, c: MediaController): String? {
        val cc = compat(ctx, c)
        val mode = repeatMode(cc)
        if (mode != -1 && cc != null) {
            cc.transportControls.setRepeatMode(
                when (mode) {
                    PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                    PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> PlaybackStateCompat.REPEAT_MODE_ONE
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                },
            )
            return null
        }
        val act = actionWith(c, REPEAT_WORDS) ?: return ctx.getString(R.string.no_repeat)
        c.transportControls.sendCustomAction(act, act.extras)
        return null
    }

    fun download(ctx: Context, c: MediaController): String? {
        val act = actionWith(c, MediaRepo.DOWNLOAD_WORDS) ?: return ctx.getString(R.string.no_download)
        if (info(ctx, c).downloaded) return ctx.getString(R.string.already_downloaded)
        c.transportControls.sendCustomAction(act, act.extras)
        return null
    }

    /** Для экрана диагностики: сырые значения, которые отдаёт плеер. */
    fun debugModes(ctx: Context, c: MediaController): Pair<Int, Int> {
        val cc = compat(ctx, c)
        return shuffleMode(cc) to repeatMode(cc)
    }

    fun debugRatingStyle(c: MediaController): Int? =
        c.metadata?.getRating(MediaMetadata.METADATA_KEY_USER_RATING)?.ratingStyle
}
