package tk.zwander.common.util.migrations

import android.content.Context
import tk.zwander.common.util.prefManager

class FrameDimAmountMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int = 3

    override fun run(context: Context) {
        val currentDimAmount = context.prefManager.wallpaperDimAmount
        val newDimAmount = currentDimAmount * 10

        context.prefManager.wallpaperDimAmount = newDimAmount
    }
}