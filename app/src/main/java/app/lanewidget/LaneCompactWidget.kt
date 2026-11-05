package app.lanewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Компактный виджет 4×1: обложка, название, исполнитель, полоска и play.
 * Фон (обложка, размытие, своё фото, прозрачность) общий с большим виджетом.
 */
class LaneCompactWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

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
    val ctx = LocalContext.current
    val launch = openIntent(s)

    // Фон готовится под фиксированный размер 4×1. Картинка обрезается по краям (Crop), не растягивается.
    val pxW = 1000
    val pxH = 250
    val photo = Background.layer(ctx, pxW, pxH)
    val cover = if (photo == null) s.blurBg?.let { Background.coverLayer(ctx, it, pxW, pxH) } else null
    val hasImage = photo != null || cover != null
    Palette.setAutoButton(if (WP.flag(ctx, WP.AUTO_BTN, false)) AutoColor.pick(photo ?: cover, Palette.cardArgb) else null)
    val card = if (hasImage) Color.Transparent
        else Color(Background.withAlphaArgb(Palette.cardArgb, Background.alpha(ctx)))

    val title = if (s.hasAccess) s.title else ctx.getString(R.string.widget_need_access)
    val artist = if (s.hasAccess) s.artist else ctx.getString(R.string.widget_need_access_hint)

    val root = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(card))
        .cornerRadius(26.dp)
        .let { if (launch != null) it.clickable(actionStartActivity(launch)) else it }

    Box(modifier = root) {
        photo?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(26.dp),
                contentScale = ContentScale.Crop,
            )
        }
        cover?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(26.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(s, 48.dp, 14.dp)
            Column(
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fit(title, 22),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Palette.text),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = fit(artist, 26),
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
            PlayButton(s.playing, 40.dp)
        }
    }
}
