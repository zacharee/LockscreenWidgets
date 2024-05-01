package tk.zwander.common.util.migrations

import android.content.Context
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.util.prefManager

class WidgetSizeMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int
        get() = 2

    override fun run(context: Context) {
        context.apply {
            @Suppress("DEPRECATION")
            val sizeInfos = prefManager.widgetSizes
            val widgets = prefManager.currentWidgets

            prefManager.currentWidgets = LinkedHashSet(
                widgets.map { widget ->
                    val sizeInfo = sizeInfos[widget.id] ?: WidgetSizeData(1, 1)

                    widget.copy(size = sizeInfo)
                }
            )
        }
    }
}