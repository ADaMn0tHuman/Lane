package app.lanewidget

import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class LaneWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = MediaRepo.read(context)
        provideContent { WidgetContent(state) }
    }
}

class LaneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LaneWidget()
}

/** Кнопки виджета: назад / играть-пауза / вперёд. */
class ControlAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = MediaRepo.pick(MediaRepo.controllers(context)) ?: return
        val t = c.transportControls
        when (parameters[CMD]) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            else -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
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
            Controls(s.playing)
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

@Composable
private fun Controls(playing: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideButton(R.drawable.ic_prev, "prev")
        Spacer(modifier = GlanceModifier.width(10.dp))
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
        Spacer(modifier = GlanceModifier.width(10.dp))
        SideButton(R.drawable.ic_next, "next")
    }
}

@Composable
private fun SideButton(res: Int, cmd: String, size: Dp = 26.dp) {
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .clickable(actionRunCallback<ControlAction>(actionParametersOf(ControlAction.CMD to cmd))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(res),
            contentDescription = null,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(ColorProvider(Palette.text)),
        )
    }
}
