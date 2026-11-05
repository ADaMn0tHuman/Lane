package app.lanewidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Раз в 12 часов проверяет последнюю сборку на GitHub и присылает уведомление, если она новее. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        try {
            val remote = UpdateChecker.latestRemote(applicationContext) ?: return Result.success()
            val local = PackageInfoCompat.getLongVersionCode(
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0),
            )
            if (remote > local) notifyNewVersion(applicationContext)
        } catch (e: Exception) {
            // Сеть недоступна или GitHub не ответил: ничего не делаем, попробуем через 12 часов.
        }
        return Result.success()
    }

    private fun notifyNewVersion(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle(ctx.getString(R.string.update_notify_title))
            .setContentText(ctx.getString(R.string.update_notify_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFY_ID, n)
    }

    companion object {
        private const val CHANNEL = "updates"
        private const val NOTIFY_ID = 2001
        private const val WORK_NAME = "update_check"

        /** Ставит проверку раз в 12 часов. Уже поставленную не сбрасывает. */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }
    }
}
