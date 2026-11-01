package app.lanewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** После перезагрузки телефона и после обновления приложения заново поднимает слежение за плеерами. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!MediaRepo.hasAccess(context)) return
        MediaRepo.rebind(context)
        KeepAliveService.start(context)
    }
}
