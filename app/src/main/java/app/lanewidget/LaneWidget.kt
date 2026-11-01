package app.lanewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.delay

class LaneWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

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

/** Нажатия на кнопки: назад / играть-пауза / вперёд / «Нравится». */
class ControlAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MediaRepo.rebind(context)
        val c = MediaRepo.pick(context, MediaRepo.controllers(context)) ?: return
        val t = c.transportControls

        when (parameters[CMD]) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            "like" -> like(context, c)
            else -> {
                val willPlay = c.playbackState?.state != PlaybackState.STATE_PLAYING
                MediaRepo.forcePlaying(willPlay)
                if (willPlay) t.play() else t.pause()
            }
        }

        // Обновляем сразу и ещё несколько раз, пока плеер успевает ответить.
        val widget = LaneWidget()
        widget.updateAll(context)
        for (wait in longArrayOf(350, 700, 1000, 800)) {
            delay(wait)
            widget.updateAll(context)
        }
    }

    private fun like(ctx: Context, c: MediaController) {
        val action = MediaRepo.findLikeAction(ctx, c)
        when {
            action != null -> {
                c.transportControls.sendCustomAction(action, action.extras)
                MediaRepo.flashLiked()
            }
            MediaRepo.alreadyLiked(c) -> toast(ctx, ctx.getString(R.string.already_liked))
            else -> toast(ctx, ctx.getString(R.string.no_like_action))
        }
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        val CMD = ActionParameters.Key<String>("cmd")
    }
}

@Composable
private fun WidgetContent(s: MediaState) {
    val ctx = LocalContext.current
    val launch: Intent? = if (!s.hasAccess) {
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        s.packageName?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
    }

    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Palette.card))
        .cornerRadius(30.dp)
        .padding(12.dp)
        .let { if (launch != null) it.clickable(actionStartActivity(launch)) else it }

    Row(modifier = root, verticalAlignment = Alignment.CenterVertically) {
        Cover(s)
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Palette.text),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = s.artist,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Palette.pink), fontSize = 13.sp),
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
            LinearProgressIndicator(
                progress = s.progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = ColorProvider(Palette.pink),
                backgroundColor = ColorProvider(Palette.track),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Controls(s)
        }
    }
}

@Composable
private fun Cover(s: MediaState) {
    val m = GlanceModifier.size(84.dp).cornerRadius(20.dp)
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
                modifier = GlanceModifier.size(32.dp),
                colorFilter = ColorFilter.tint(ColorProvider(Palette.pink)),
            )
        }
    }
}

/** Четыре кнопки в равных ячейках: их центры стоят на одинаковом расстоянии и симметричны полосе. */
@Composable
private fun Controls(s: MediaState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell { SideButton(R.drawable.ic_prev, "prev", Palette.text) }
        Cell { PlayButton(s.playing) }
        Cell { SideButton(R.drawable.ic_next, "next", Palette.text) }
        Cell {
            if (s.flash) {
                SideButton(R.drawable.ic_check, "like", Palette.pink)
            } else {
                SideButton(R.drawable.ic_plus, "like", Palette.text)
            }
        }
    }
}

@Composable
private fun RowScope.Cell(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.defaultWeight().height(44.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PlayButton(playing: Boolean) {
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .background(ColorProvider(Palette.pink))
            .cornerRadius(22.dp)
            .clickable(actionRunCallback<ControlAction>(actionParametersOf(ControlAction.CMD to "toggle"))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
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
            modifier = GlanceModifier.size(26.dp),
            colorFilter = ColorFilter.tint(ColorProvider(tint)),
        )
    }
}
