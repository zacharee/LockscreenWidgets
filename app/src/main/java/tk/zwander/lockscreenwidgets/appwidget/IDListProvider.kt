package tk.zwander.lockscreenwidgets.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.services.IDWidgetService
import tk.zwander.lockscreenwidgets.util.prefManager

class IDListProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_UPDATE_IDS = "UPDATE_IDS"
        const val EXTRA_IDS = "ID_LIST"

        fun sendUpdate(context: Context) {
            val intent = Intent(ACTION_UPDATE_IDS)
            intent.component = ComponentName(context, IDListProvider::class.java)

            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_UPDATE_IDS -> {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, IDListProvider::class.java)
                manager.notifyAppWidgetViewDataChanged(manager.getAppWidgetIds(component), R.id.id_list)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val view = RemoteViews(context.packageName, R.layout.id_list_widget_layout)
        view.setRemoteAdapter(R.id.id_list, Intent(context, IDWidgetService::class.java))

        appWidgetIds.forEach {
            appWidgetManager.updateAppWidget(it, view)
        }
    }
}