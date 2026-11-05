package app.lanewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.delay

class LaneWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = MediaRepo.read(context)
        provideContent { WidgetContent(state) }
    }
}

class LaneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LaneWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MediaRepo.rebind(context)
    }
}

/** Нажатия на кнопки: назад / играть-пауза / вперёд / «Нравится» / перемешать / повтор / скачать. */
class ControlAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MediaRepo.rebind(context)
        val c = MediaRepo.pick(context, MediaRepo.controllers(context)) ?: return
        val t = c.transportControls

        val wasPlaying = MediaRepo.isPlayingState(c.playbackState?.state)
        var keepPaused = false

        val message: String? = when (parameters[CMD]) {
            "prev" -> {
                if (wasPlaying) {
                    t.skipToPrevious()
                } else {
                    // На паузе: если трек идёт дольше трёх секунд, просто в начало, иначе на трек назад.
                    // Музыку в обоих случаях не включаем.
                    val position = c.playbackState?.position ?: 0L
                    if (position > RESTART_THRESHOLD_MS) {
                        t.seekTo(0)
                        MediaRepo.forceStartOfTrack()
                    } else {
                        t.skipToPrevious()
                        MediaRepo.forceStartOfTrack()
                        keepPaused = true
                    }
                }
                null
            }
            "next" -> {
                t.skipToNext()
                if (!wasPlaying) {
                    MediaRepo.forceStartOfTrack()
                    keepPaused = true
                }
                null
            }
            "like" -> PlayerControls.toggleLike(context, c)
            "shuffle" -> PlayerControls.toggleShuffle(context, c)
            "repeat" -> PlayerControls.cycleRepeat(context, c)
            "switch" -> { switchPlayer(context, c); null }
            "download" -> PlayerControls.download(context, c)
            else -> {
                val willPlay = !MediaRepo.isPlayingState(c.playbackState?.state)
                MediaRepo.forcePlaying(willPlay)
                if (willPlay) t.play() else t.pause()
                null
            }
        }
        if (message != null) toast(context, message)

        if (keepPaused) MediaRepo.forcePlaying(false)

        // Одно мгновенное обновление, а дальше виджет обновят колбэки плеера (MediaListener).
        WidgetUpdater.refresh(context)

        // Многие плееры после перехода на другой трек сами начинают играть. Следим и ставим на паузу.
        if (keepPaused) holdPause(c)

        // Повторная проверка: для режимов (перемешать, повтор) через 0,5 с, для остального через 1 с.
        // Страхует случай, когда плеер не прислал колбэк.
        if (parameters[CMD] == "shuffle" || parameters[CMD] == "repeat") {
            delay(500)
        } else {
            delay(1000)
        }
        WidgetUpdater.refresh(context)
    }

    /**
     * После перехода на другой трек на паузе ждём, не начнёт ли плеер играть сам, и ставим на паузу.
     * Паузу ставим в момент PLAYING или BUFFERING, чтобы звук не успел пойти. Если за секунду
     * плеер так и остался на паузе, значит он послушно не стал играть, и можно заканчивать.
     */
    private suspend fun holdPause(c: MediaController) {
        val t = c.transportControls
        val start = SystemClock.elapsedRealtime()
        var pausedByUs = false
        while (SystemClock.elapsedRealtime() - start < 2500) {
            delay(70)
            val st = c.playbackState?.state
            if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) {
                t.pause()
                pausedByUs = true
            } else if ((st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_STOPPED) &&
                (pausedByUs || SystemClock.elapsedRealtime() - start > 1000)
            ) {
                break
            }
        }
    }

    /** Текущий плеер останавливается, следующий в списке начинает играть. Звук не накладывается. */
    private fun switchPlayer(ctx: Context, from: MediaController) {
        val list = MediaRepo.controllers(ctx)
        val i = list.indexOfFirst { it.packageName == from.packageName }
        if (i < 0 || list.size < 2) return
        val next = list[(i + 1) % list.size]
        if (MediaRepo.isPlayingState(from.playbackState?.state)) from.transportControls.pause()
        MediaRepo.markActive(ctx, next.packageName)
        MediaRepo.forcePlaying(true)
        next.transportControls.play()
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        val CMD = ActionParameters.Key<String>("cmd")

        /** Если трек идёт дольше этого времени, «назад» на паузе перематывает в начало, а не на прошлый трек. */
        private const val RESTART_THRESHOLD_MS = 3000L
    }
}

/** Что происходит по нажатию на сам виджет: открыть плеер (или экран выдачи доступа). */
@Composable
internal fun openIntent(s: MediaState): Intent? {
    val ctx = LocalContext.current
    return when {
        !s.hasAccess -> Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        s.packageName != null -> Intent(ctx, OpenPlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else -> null
    }
}

/** Обрезает текст многоточием, если он не вмещается. Ширина приблизительная, по размеру виджета. */
private fun fit(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

@Composable
private fun WidgetContent(s: MediaState) {
    val ctx = LocalContext.current
    val launch = openIntent(s)
    val size = LocalSize.current
    val pxW = if (size.width.value < 10f) 1080 else (size.width.value * 3).toInt().coerceAtMost(1080)
    val pxH = if (size.height.value < 10f) 408 else (size.height.value * 3).toInt().coerceAtMost(1080)

    // Фон: своё фото имеет приоритет, затем размытая обложка, затем цвет пака. Прозрачность общая.
    val alpha = Background.alpha(ctx)
    val photo = Background.layer(ctx, pxW, pxH)
    val blur = if (photo == null) s.blurBg?.let { Background.withAlpha(it, pxW, pxH, alpha) } else null
    val hasImage = photo != null || blur != null
    val cardColor = if (hasImage) Color.Transparent
        else Color(Background.withAlphaArgb(Palette.cardArgb, alpha))

    val textWidth = size.width.value - 24f - 92f - 6f
    val titleMax = (textWidth / 9.5f).toInt().coerceAtLeast(6)
    val artistMax = (textWidth / 7.5f).toInt().coerceAtLeast(6)

    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(cardColor))
        .cornerRadius(30.dp)
        .let { if (launch != null) it.clickable(actionStartActivity(launch)) else it }

    Box(modifier = root) {
        photo?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(30.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
        blur?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(30.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Обложка 92 dp. Текст начинается на расстоянии 12 + 92 + 6 = 110 dp от края.
            Column(horizontalAlignment = Alignment.Start) {
                Cover(s, 92.dp, 20.dp)
                if (WP.shown(ctx, WP.SWITCH) && s.appIcon != null) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    AppBadge(s, 32.dp)
                }
            }
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fit(s.title, titleMax),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Palette.text),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = fit(s.artist, artistMax),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Palette.pink), fontSize = 13.sp),
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                ProgressRow(s)
                Spacer(modifier = GlanceModifier.height(2.dp))
                // Второй ряд скрывается только на очень низком виджете.
                Controls(s, showMini = size.height.value >= 120f)
            }
        }
    }
}

/** Значок приложения, которое сейчас играет. Тап переключает плеер. */
@Composable
internal fun AppBadge(s: MediaState, size: Dp) {
    val icon = s.appIcon ?: return
    Box(
        modifier = GlanceModifier
            .size(size)
            .clickable(actionRunCallback<ControlAction>(actionParametersOf(ControlAction.CMD to "switch"))),
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(size).cornerRadius(size / 4),
        )
    }
}

@Composable
internal fun Cover(s: MediaState, size: Dp, radius: Dp) {
    val m = GlanceModifier.size(size).cornerRadius(radius)
    if (s.cover != null) {
        Image(
            provider = ImageProvider(s.cover),
            contentDescription = null,
            modifier = m,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = m.background(ColorProvider(Palette.cover)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_music),
                contentDescription = null,
                modifier = GlanceModifier.size(size / 2.6f),
                colorFilter = ColorFilter.tint(ColorProvider(Palette.pink)),
            )
        }
    }
}

/** Время «прошло», полоска прогресса и общая длительность трека. */
@Composable
private fun ProgressRow(s: MediaState) {
    val timeWidth = if (s.durationMs >= 3_600_000L) 46.dp else 34.dp
    val elapsed = MediaRepo.formatTime(s.positionMs)
    val total = if (s.durationMs > 0) MediaRepo.formatTime(s.durationMs) else "--:--"
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = elapsed,
            maxLines = 1,
            modifier = GlanceModifier.width(timeWidth),
            style = TextStyle(color = ColorProvider(Palette.dim), fontSize = 11.sp, textAlign = TextAlign.Start),
        )
        Image(
            provider = ImageProvider(ProgressBitmap.draw(s.progress)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.defaultWeight().height(8.dp),
        )
        Text(
            text = total,
            maxLines = 1,
            modifier = GlanceModifier.width(timeWidth),
            style = TextStyle(color = ColorProvider(Palette.dim), fontSize = 11.sp, textAlign = TextAlign.End),
        )
    }
}

/**
 * Две строки по четыре ячейки. Скрытая кнопка оставляет пустую ячейку, поэтому остальные
 * не съезжают. Кнопка «играть» есть всегда.
 */
@Composable
private fun Controls(s: MediaState, showMini: Boolean) {
    val ctx = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cell(40.dp) { if (WP.shown(ctx, WP.PREV)) SideButton(R.drawable.ic_prev, "prev", Palette.text) }
            Cell(40.dp) { PlayButton(s.playing, 40.dp) }
            Cell(40.dp) { if (WP.shown(ctx, WP.NEXT)) SideButton(R.drawable.ic_next, "next", Palette.text) }
            Cell(40.dp) {
                if (WP.shown(ctx, WP.LIKE)) {
                    if (s.liked) {
                        SideButton(R.drawable.ic_check, "like", Palette.pink)
                    } else {
                        SideButton(R.drawable.ic_plus, "like", Palette.text)
                    }
                }
            }
        }
        if (showMini) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell(26.dp) {
                    if (WP.shown(ctx, WP.SHUFFLE)) {
                        MiniButton(R.drawable.ic_shuffle, "shuffle", miniTint(s.shuffleSupported, s.shuffleOn))
                    }
                }
                Cell(26.dp) { }
                Cell(26.dp) {
                    if (WP.shown(ctx, WP.REPEAT)) {
                        MiniButton(
                            if (s.repeatMode == 1) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                            "repeat",
                            miniTint(s.repeatSupported, s.repeatMode != 0),
                        )
                    }
                }
                Cell(26.dp) {
                    if (WP.shown(ctx, WP.DOWNLOAD)) {
                        MiniButton(R.drawable.ic_download, "download", miniTint(s.downloadSupported, s.downloaded))
                    }
                }
            }
        }
    }
}

/** Розовая: включено. Серая: доступно, но выключено. Тёмная: плеер этого не умеет. */
private fun miniTint(supported: Boolean, on: Boolean): Color = when {
    !supported -> Palette.muted
    on -> Palette.pink
    else -> Palette.dim
}

@Composable
private fun RowScope.Cell(height: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.defaultWeight().height(height),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun PlayButton(playing: Boolean, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ColorProvider(Palette.pink))
            .cornerRadius(size / 2)
            .clickable(actionRunCallback<ControlAction>(actionParametersOf(ControlAction.CMD to "toggle"))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = null,
            modifier = GlanceModifier.size(size / 1.8f),
            colorFilter = ColorFilter.tint(ColorProvider(Palette.onPink)),
        )
    }
}

@Composable
private fun SideButton(res: Int, cmd: String, tint: Color) {
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .clickable(actionRunCallback<ControlAction>(actionParametersOf(ControlAction.CMD to cmd))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(res),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
        )
    }
}

/** Маленькая кнопка нижнего ряда. Область нажатия шире самой иконки. */
@Composable
private fun MiniButton(res: Int, cmd: String, tint: Color) {
    Box(
        modifier = GlanceModifier
            .width(40.dp)
            .height(26.dp)
            .clickable(actionRunCallback<ControlAction>(actionParametersOf(ControlAction.CMD to cmd))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(res),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
        )
    }
}
