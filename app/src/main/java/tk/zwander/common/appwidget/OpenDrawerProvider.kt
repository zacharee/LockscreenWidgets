package tk.zwander.common.appwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import tk.zwander.common.receivers.OpenDrawerReceiver
import tk.zwander.lockscreenwidgets.R

class OpenDrawerProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val view = RemoteViews(context.packageName, R.layout.open_drawer_layout)
        view.setOnClickPendingIntent(
            R.id.open_drawer_button,
            PendingIntent.getBroadcast(
                context,
                100,
                Intent(context, OpenDrawerReceiver::class.java).apply {
                    action = OpenDrawerReceiver.OPEN_ACTION
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )

        appWidgetIds.forEach {
            appWidgetManager.updateAppWidget(it, view)
        }
    }
}