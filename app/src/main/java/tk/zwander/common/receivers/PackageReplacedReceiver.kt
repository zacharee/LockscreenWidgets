package tk.zwander.common.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import tk.zwander.common.util.appWidgetManager
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val ids = context.appWidgetManager.getAppWidgetIds(
                ComponentName(context, WidgetStackProvider::class.java),
            )

            WidgetStackProvider.update(
                context = context,
                ids = ids,
            )
        }
    }
}
