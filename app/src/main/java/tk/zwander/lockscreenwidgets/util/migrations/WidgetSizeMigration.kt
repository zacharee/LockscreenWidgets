package tk.zwander.lockscreenwidgets.util.migrations

import android.content.Context
import tk.zwander.lockscreenwidgets.data.WidgetSizeData
import tk.zwander.lockscreenwidgets.util.prefManager

class WidgetSizeMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int
        get() = 2

    override fun run(context: Context) {
        context.apply {
            val sizeInfos = prefManager.widgetSizes
            val widgets = prefManager.currentWidgets

            widgets.forEach { widget ->
                val sizeInfo = sizeInfos[widget.id] ?: WidgetSizeData(1, 1)

                widget.size = sizeInfo
            }

            prefManager.currentWidgets = widgets
        }
    }
}