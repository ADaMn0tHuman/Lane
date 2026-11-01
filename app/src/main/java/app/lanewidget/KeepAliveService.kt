package app.lanewidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Служба переднего плана с тихим уведомлением. Её единственная задача: поднять приоритет
 * процесса, чтобы система не убивала приложение вместе с сервисом, который следит за плеерами.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.keepalive_channel), NotificationManager.IMPORTANCE_MIN),
            )
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(getString(R.string.keepalive_title))
                .setContentText(getString(R.string.keepalive_text))
                .setOngoing(true)
                .setContentIntent(open)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification)
            }
            running = true
            lastError = null
        } catch (e: Exception) {
            // Система не разрешила запуск из фона. Виджет продолжит работать и без службы.
            running = false
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "keepalive"
        private const val ID = 1001

        /** Реально ли служба сейчас работает (а не просто «её попросили запуститься»). */
        @Volatile var running = false
        @Volatile var lastError: String? = null

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
            } catch (e: Exception) {
                running = false
                lastError = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        /** Запускает службу и через 3 секунды проверяет, что она правда поднялась. Если нет, пробует ещё раз. */
        fun ensure(ctx: Context) {
            if (running) return
            val app = ctx.applicationContext
            start(app)
            Handler(Looper.getMainLooper()).postDelayed({ if (!running) start(app) }, 3000)
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, KeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
