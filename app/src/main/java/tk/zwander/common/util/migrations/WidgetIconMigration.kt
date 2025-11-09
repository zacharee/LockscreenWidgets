package tk.zwander.common.util.migrations

import android.content.Context
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.IconPrefs

class WidgetIconMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int = 5

    override fun run(context: Context) {
        context.prefManager.currentWidgets = LinkedHashSet(migrateCollection(context, context.prefManager.currentWidgets))
        context.prefManager.drawerWidgets = LinkedHashSet(migrateCollection(context, context.prefManager.drawerWidgets))

        context.prefManager.currentSecondaryFramesWithDisplay.forEach { (frameId, _) ->
            FramePrefs.setWidgetsForFrame(context, frameId, migrateCollection(context, FramePrefs.getWidgetsForFrame(context, frameId)))
        }
    }

    private fun migrateCollection(context: Context, widgets: Collection<WidgetData>): Collection<WidgetData> {
        return widgets.map { data ->
            IconPrefs.setIconForWidget(context, data.id, data.getNonOverriddenIcon(context))
            data.copy(
                icon = null,
                iconRes = null,
            )
        }
    }
}
