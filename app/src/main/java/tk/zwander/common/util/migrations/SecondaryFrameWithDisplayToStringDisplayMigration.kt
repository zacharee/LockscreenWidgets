package tk.zwander.common.util.migrations

import android.content.Context
import tk.zwander.common.util.prefManager

class SecondaryFrameWithDisplayToStringDisplayMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int = 8

    @Suppress("DEPRECATION")
    override fun run(context: Context) {
        val oldFrameData = context.prefManager.currentSecondaryFramesWithDisplay

        context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(oldFrameData.map { (key, value) -> key to "$value" }.toMap())

        context.prefManager.currentSecondaryFramesWithDisplay = hashMapOf()
    }
}
