package tk.zwander.common.util.migrations

import android.content.Context
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

@Suppress("DEPRECATION")
class CurrentFrameIndexMigration : Migration {
    override val runBelowDatabaseVersion: Int = 9

    override fun run(context: Context) {
        if (context.prefManager.contains(PrefManager.KEY_CURRENT_PAGE)) {
            FrameSpecificPreferences[MainWidgetFrameDelegate.ID].currentIndex = context.prefManager.currentPage

            context.prefManager.remove(PrefManager.KEY_CURRENT_PAGE)
        }
    }
}
