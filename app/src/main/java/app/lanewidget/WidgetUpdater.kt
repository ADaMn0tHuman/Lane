package app.lanewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Быстрое обновление виджетов в обход GlanceAppWidget.update()/updateAll().
 *
 * Почему так: update() у Glance идёт через сессию и WorkManager (каждый вызов закрывает прошлую
 * сессию и ставит новую задачу в очередь), из-за чего задержка то мелкая, то в разы больше.
 * Здесь мы один раз собираем RemoteViews через compose() и сразу отдаём их в AppWidgetManager.
 * Если по какой-то причине прямой путь упал, откатываемся на обычный updateAll().
 *
 * Обновляются оба виджета: большой и компактный.
 */
object WidgetUpdater {

    private val mutex = Mutex()
    private val generation = AtomicInteger()

    // Статистика для экрана диагностики.
    @Volatile var lastOkAt = 0L
    @Volatile var lastError: String? = null
    @Volatile var updates = 0
    @Volatile var fallbacks = 0

    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val mine = generation.incrementAndGet()
        mutex.withLock {
            // Пока мы ждали очередь, пришёл более свежий запрос. Он отрисует актуальное состояние.
            if (generation.get() != mine) return
            withContext(Dispatchers.Main) {
                val gm = GlanceAppWidgetManager(app)
                val awm = AppWidgetManager.getInstance(app)
                render(app, gm, awm, LaneWidget::class.java) { LaneWidget() }
                render(app, gm, awm, LaneCompactWidget::class.java) { LaneCompactWidget() }
            }
        }
        // Идёт плавная смена обложки: просим следующий кадр.
        if (CoverFade.running) {
            delay(60)
            refresh(app)
        }
    }

    private suspend fun render(
        app: Context,
        gm: GlanceAppWidgetManager,
        awm: AppWidgetManager,
        cls: Class<out GlanceAppWidget>,
        make: () -> GlanceAppWidget,
    ) {
        try {
            val ids = gm.getGlanceIds(cls)
            for (id in ids) {
                val views = make().compose(app, id)
                awm.updateAppWidget(gm.getAppWidgetId(id), views)
            }
            if (ids.isNotEmpty()) {
                lastOkAt = System.currentTimeMillis()
                lastError = null
                updates++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            fallbacks++
            try {
                make().updateAll(app)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
            }
        }
    }
}
