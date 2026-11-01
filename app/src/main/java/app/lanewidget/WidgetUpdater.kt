package app.lanewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Быстрое обновление виджета в обход GlanceAppWidget.update()/updateAll().
 *
 * Почему так: update() у Glance идёт через сессию и WorkManager (каждый вызов закрывает прошлую
 * сессию и ставит новую задачу в очередь), из-за чего задержка то мелкая, то в разы больше.
 * Здесь мы один раз собираем RemoteViews через compose() и сразу отдаём их в AppWidgetManager.
 * Если по какой-то причине прямой путь упал, откатываемся на обычный updateAll().
 */
object WidgetUpdater {

    private val mutex = Mutex()
    private val generation = AtomicInteger()

    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val mine = generation.incrementAndGet()
        mutex.withLock {
            // Пока мы ждали очередь, пришёл более свежий запрос. Он отрисует актуальное состояние.
            if (generation.get() != mine) return

            try {
                withContext(Dispatchers.Main) {
                    val manager = GlanceAppWidgetManager(app)
                    val ids = manager.getGlanceIds(LaneWidget::class.java)
                    if (ids.isEmpty()) return@withContext
                    val awm = AppWidgetManager.getInstance(app)
                    for (id in ids) {
                        val views = LaneWidget().compose(app, id)
                        awm.updateAppWidget(manager.getAppWidgetId(id), views)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    LaneWidget().updateAll(app)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                }
            }
        }
    }
}
