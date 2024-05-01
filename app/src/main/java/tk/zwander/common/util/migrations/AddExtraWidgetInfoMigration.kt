package tk.zwander.common.util.migrations

import android.appwidget.AppWidgetManager
import android.content.Context
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.loadPreviewOrIcon
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.toBase64

class AddExtraWidgetInfoMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int
        get() = 1

    override fun run(context: Context) {
        val manager = context.appWidgetManager
        val currentWidgets = context.prefManager.currentWidgets

        context.prefManager.currentWidgets = LinkedHashSet(
            currentWidgets.mapNotNull { widget ->
                if (widget.type == WidgetType.WIDGET) {
                    context.migrateWidget(widget, manager)
                } else {
                    widget
                }
            },
        )
    }

    private fun Context.migrateWidget(widget: WidgetData, manager: AppWidgetManager): WidgetData? {
        val widgetInfo = manager.getAppWidgetInfo(widget.id)

        if (widgetInfo == null) {
            logUtils.normalLog("Unable to migrate widget $widget: info is null.")
            return null
        }

        return widget.copy(
            widgetProvider = widget.widgetProvider ?: widgetInfo.provider.flattenToString(),
            label = widget.label ?: widgetInfo.loadLabel(packageManager),
            icon = widget.icon ?: widgetInfo.loadPreviewOrIcon(this, 0)?.toBase64(),
        )
    }
}