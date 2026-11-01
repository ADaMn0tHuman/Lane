package app.lanewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** Компактный виджет 4×1: обложка с значком плеера, название, исполнитель, полоска и play. */
class LaneCompactWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = MediaRepo.read(context)
        provideContent { CompactContent(state) }
    }
}

class LaneCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LaneCompactWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MediaRepo.rebind(context)
    }
}

@Composable
private fun CompactContent(s: MediaState) {
    val launch = openIntent(s)

    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(Palette.card))
        .cornerRadius(26.dp)
        .let { if (launch != null) it.clickable(actionStartActivity(launch)) else it }

    // Элементы ужимаются под фактическую высоту, иначе круг кнопки обрезается и становится овалом.
    val h = LocalSize.current.height
    val unit = if (h - 16.dp < 48.dp) h - 16.dp else 48.dp

    Box(modifier = root) {
        val blur = s.blurBg
        if (blur != null) {
            Image(
                provider = ImageProvider(blur),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(26.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.size(unit), contentAlignment = Alignment.BottomStart) {
                Cover(s, unit, 14.dp)
                AppBadge(s, 18.dp)
            }
            Column(
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Palette.text),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = s.artist,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Palette.pink), fontSize = 12.sp),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Image(
                    provider = ImageProvider(ProgressBitmap.draw(s.progress)),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                )
            }
            PlayButton(s.playing, unit.coerceAtMost(40.dp))
        }
    }
}
