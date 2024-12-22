package tk.zwander.lockscreenwidgets.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import tk.zwander.common.util.appWidgetManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.services.IDWidgetService

/**
 * A widget-form alternative to the debug ID overlay. This is a keyguard-only widget that can be added
 * to Lockscreen Widgets to view the ID list without distrupting the widget frame.
 */
class IDListProvider : AppWidgetProvider() {
    companion object {
        fun sendUpdate(context: Context) {
            //There's a new list of IDs; make sure the factory is notified
            val manager = context.appWidgetManager
            val component = ComponentName(context, IDListProvider::class.java)
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(manager.getAppWidgetIds(component), R.id.id_list)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val view = RemoteViews(context.packageName, R.layout.id_list_widget_layout)
        @Suppress("DEPRECATION")
        view.setRemoteAdapter(R.id.id_list, Intent(context, IDWidgetService::class.java))

        appWidgetManager.updateAppWidget(appWidgetIds, view)
    }
}