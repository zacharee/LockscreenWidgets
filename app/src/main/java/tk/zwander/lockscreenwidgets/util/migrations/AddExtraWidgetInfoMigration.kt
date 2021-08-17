package tk.zwander.lockscreenwidgets.util.migrations

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetType
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.logUtils
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.util.toBase64

class AddExtraWidgetInfoMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int
        get() = 1

    override fun run(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val host = WidgetHostCompat.getInstance(context, 1003)

        val currentWidgets = context.prefManager.currentWidgets

        currentWidgets.forEach { widget ->
            if (widget.type == WidgetType.WIDGET) {
                context.migrateWidget(widget, manager, host)
            }
        }

        context.prefManager.currentWidgets = currentWidgets
    }

    private fun Context.migrateWidget(widget: WidgetData, manager: AppWidgetManager, host: AppWidgetHost) {
        val widgetInfo = manager.getAppWidgetInfo(widget.id)

        if (widgetInfo == null) {
            logUtils.normalLog("Unable to migrate widget $widget: info is null.")
            return
        }

        if (widget.widgetProvider == null) {
            widget.widgetProvider = widgetInfo.provider.flattenToString()
        }

        if (widget.label == null) {
            widget.label = widgetInfo.loadLabel(packageManager)
        }

        if (widget.icon == null) {
            widget.icon = widgetInfo.loadPreviewImage(this, 0).toBitmap().toBase64()
        }
    }
}