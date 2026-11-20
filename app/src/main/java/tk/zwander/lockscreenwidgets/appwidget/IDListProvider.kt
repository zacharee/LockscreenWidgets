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
import tk.zwander.common.util.mainHandler
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.IDData

/**
 * A widget-form alternative to the debug ID overlay. This is a keyguard-only widget that can be added
 * to Lockscreen Widgets to view the ID list without disrupting the widget frame.
 */
class IDListProvider : AppWidgetProvider() {
    companion object {
        private const val UPDATE_DEBOUNCE_MS = 1_000L
        private const val INTERACTION_RETRY_MS = 750L

        private var updateScheduled = false

        private val updateRunnable: Runnable = object : Runnable {
            override fun run() {
                //Never replace the collection while the list is being touched/scrolled.
                //The platform's RemoteCollectionItemsAdapter doesn't bounds-check positions,
                //so a shrinking collection can crash an in-progress scroll.
                if (DebugIDsManager.isIdListInteracting) {
                    mainHandler.postDelayed(this, INTERACTION_RETRY_MS)
                    return
                }

                updateScheduled = false

                updateAllWidgets(App.instance)
            }
        }

        private fun currentWidgetIds(context: Context): IntArray {
            return context.appWidgetManager.getAppWidgetIds(
                ComponentName(context, IDListProvider::class.java),
            )
        }

        /**
         * Update every instance of this provider right now.
         *
         * Used by [onUpdate]/[onRestored], which run inside a broadcast receiver: once
         * onReceive() returns, the system is free to kill the process, so a delayed post
         * from there isn't guaranteed to ever run.
         */
        private fun updateAllWidgets(context: Context) {
            val appWidgetIds = currentWidgetIds(context)

            //This is the full provider-wide list across all hosts, unlike onUpdate()'s IDs.
            DebugIDsManager.setExistingIdListWidgetIds(appWidgetIds.toList())

            updateWidget(context, context.appWidgetManager, appWidgetIds)
        }

        fun refreshWidgetIds(context: Context) {
            DebugIDsManager.setExistingIdListWidgetIds(currentWidgetIds(context).toList())
        }

        fun sendUpdate() {
            mainHandler.post {
                if (!updateScheduled) {
                    updateScheduled = true
                    mainHandler.postDelayed(updateRunnable, UPDATE_DEBOUNCE_MS)
                }
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            appWidgetIds.forEach { appWidgetId ->
                val view = RemoteViews(context.packageName, R.layout.id_list_widget_layout)

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
                                    setTextColor(
                                        R.id.id_list_item,
                                        when (debugId.type) {
                                            IDData.IDType.ADDED -> Color.GREEN
                                            IDData.IDType.REMOVED -> Color.RED
                                            IDData.IDType.SAME -> Color.WHITE
                                        },
                                    )
                                }

                                addItem(debugId.id.hashCode().toLong(), itemView)
                            }
                        }
                        .build(),
                )

                appWidgetManager.updateAppWidget(appWidgetId, view)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // appWidgetIds only covers the host that sent this broadcast, so query the full set.
        updateAllWidgets(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        DebugIDsManager.addIdListWidgetIds(newWidgetIds.toList())
        updateAllWidgets(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        DebugIDsManager.removeIdListWidgetIds(appWidgetIds.toList())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DebugIDsManager.clearIdListWidgetIds()
    }
}
