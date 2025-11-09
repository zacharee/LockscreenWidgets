package tk.zwander.common.util.migrations

import android.content.Context
import android.view.Display
import tk.zwander.common.util.prefManager

class SecondaryFrameToFrameWithDisplayMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int = 7

    override fun run(context: Context) {
        @Suppress("DEPRECATION")
        val oldFrameData = context.prefManager.currentSecondaryFrames

        context.prefManager.currentSecondaryFramesWithDisplay = HashMap(oldFrameData.associateWith { Display.DEFAULT_DISPLAY })

        @Suppress("DEPRECATION")
        context.prefManager.currentSecondaryFrames = listOf()
    }
}