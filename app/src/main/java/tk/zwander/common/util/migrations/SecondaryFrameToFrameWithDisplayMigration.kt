package tk.zwander.common.util.migrations

import android.content.Context
import android.view.Display
import tk.zwander.common.util.prefManager

class SecondaryFrameToFrameWithDisplayMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int = 7

    @Suppress("DEPRECATION")
    override fun run(context: Context) {
        val oldFrameData = context.prefManager.currentSecondaryFrames

        context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(oldFrameData.associateWith { "${Display.DEFAULT_DISPLAY}" })

        context.prefManager.currentSecondaryFrames = listOf()
    }
}