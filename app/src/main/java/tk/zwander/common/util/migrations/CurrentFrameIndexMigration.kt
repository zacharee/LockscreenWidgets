package tk.zwander.common.util.migrations

import android.content.Context
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate

@Suppress("DEPRECATION")
class CurrentFrameIndexMigration : Migration {
    // This originally shipped with database version 9, but the version check change in 4.4.0
    // (runBelowDatabaseVersion > storedVersion) meant it was skipped by the 9 -> 10 upgrade. Keep
    // this at 11 so the repair runs for every existing install. The checks below make that safe.
    override val runBelowDatabaseVersion: Int = 11

    override fun run(context: Context) {
        if (!context.prefManager.contains(PrefManager.KEY_CURRENT_PAGE)) {
            return
        }

        val framePrefs = FrameSpecificPreferences[WidgetFrameDelegate.ID]

        // Only copy the legacy page if this frame has never stored its own index. Anyone who
        // upgraded past the original migration and has since changed pages must keep their value.
        if (!framePrefs.framePreferences.contains(PrefManager.KEY_CURRENT_PAGE)) {
            framePrefs.currentIndex = context.prefManager.currentPage
        }

        context.prefManager.remove(PrefManager.KEY_CURRENT_PAGE)
    }
}
