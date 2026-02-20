package tk.zwander.lockscreenwidgets.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ServiceManager
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R

class WidgetStackProvider : AppWidgetProvider() {
    private val appWidgetService by lazy {
        IAppWidgetService.Stub.asInterface(
            ServiceManager.getService(Context.APPWIDGET_SERVICE),
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_SWAP_INDEX) {
            val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)

            widgetIds?.forEach { widgetId ->
                val allStacks = context.prefManager.widgetStackWidgets
                val stackedWidgets = (allStacks[widgetId] ?: LinkedHashSet()).toList()

                val index = (context.prefManager.widgetStackIndices[widgetId] ?: 0)
                    .coerceAtMost(stackedWidgets.lastIndex)

                val newIndex = if (index + 1 <= stackedWidgets.lastIndex) {
                    index + 1
                } else {
                    0
                }

                context.prefManager.widgetStackIndices = context.prefManager.widgetStackIndices.apply {
                    this[widgetId] = newIndex
                }
            }

            onReceive(context, intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE))
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val view = RemoteViews(context.packageName, R.layout.stack_widget)
            val stackedWidgets = (context.prefManager.widgetStackWidgets[appWidgetId] ?: LinkedHashSet()).toList()

            val index = (context.prefManager.widgetStackIndices[appWidgetId] ?: 0)
                .coerceAtMost(stackedWidgets.lastIndex)

            stackedWidgets[index].let { widget ->
                val stackView = appWidgetService.getAppWidgetViews(context.packageName, widget.id)
                view.removeAllViews(R.id.widget_root)
                view.addView(
                    R.id.widget_root,
                    stackView,
                )
                try {
                    appWidgetManager.updateAppWidget(widget.id, stackView)
                } catch (_: Exception) {}
            }

            view.setOnClickPendingIntent(
                R.id.stack_swap,
                PendingIntentCompat.getBroadcast(
                    context,
                    appWidgetId,
                    Intent(context, WidgetStackProvider::class.java)
                        .setAction(ACTION_SWAP_INDEX)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId)),
                    0,
                    false,
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, view)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        context.prefManager.widgetStackWidgets[appWidgetId]?.forEach { stackWidget ->
            appWidgetManager.updateAppWidgetOptions(stackWidget.id, newOptions)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        val newWidgets = context.prefManager.widgetStackWidgets
        appWidgetIds.forEach { appWidgetId ->
            context.widgetHostCompat.deleteAppWidgetId(appWidgetId)
            newWidgets.remove(appWidgetId)
        }
        context.prefManager.widgetStackWidgets = newWidgets
    }

    companion object {
        const val ACTION_SWAP_INDEX = "${BuildConfig.APPLICATION_ID}.intent.action.SWAP_INDEX"
    }
}