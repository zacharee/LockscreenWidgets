package tk.zwander.lockscreenwidgets.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.view.Display
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import tk.zwander.common.util.DebugIDsManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.IDData

/**
 * A widget-form alternative to the debug ID overlay. This is a keyguard-only widget that can be added
 * to Lockscreen Widgets to view the ID list without disrupting the widget frame.
 */
class IDListProvider : AppWidgetProvider() {
    companion object {
        fun sendUpdate(context: Context) {
            //There's a new list of IDs; make sure the factory is notified
            val manager = context.appWidgetManager
            val component = ComponentName(context, IDListProvider::class.java)

            updateWidget(
                context,
                manager,
                manager.getAppWidgetIds(component),
            )
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            val view = RemoteViews(context.packageName, R.layout.id_list_widget_layout)

            appWidgetIds.forEach { appWidgetId ->
                val displayId = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    .getInt("displayId", Display.DEFAULT_DISPLAY)

                RemoteViewsCompat.setRemoteAdapter(
                    context,
                    view,
                    appWidgetId,
                    R.id.id_list,
                    RemoteViewsCompat.RemoteCollectionItems.Builder()
                        .setHasStableIds(false)
                        .setViewTypeCount(1)
                        .apply {
                            DebugIDsManager.items.value[displayId]?.forEach { debugId ->
                                val itemView = RemoteViews(context.packageName, R.layout.id_list_widget_item).apply {
                                    setTextViewText(R.id.id_list_item, debugId.id)
                                    setTextColor(R.id.id_list_item, when (debugId.type) {
                                        IDData.IDType.ADDED -> Color.GREEN
                                        IDData.IDType.REMOVED -> Color.RED
                                        IDData.IDType.SAME -> Color.WHITE
                                    })
                                }

                                addItem(debugId.id.hashCode().toLong(), itemView)
                            }
                        }
                        .build(),
                )
            }

            appWidgetManager.updateAppWidget(appWidgetIds, view)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidget(context, appWidgetManager, appWidgetIds)
    }
}