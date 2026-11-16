package tk.zwander.common.util.migrations

import android.content.Context
import tk.zwander.common.util.prefManager

class SecondaryFrameWithDisplayToStringDisplayMigration : Migration {
    override val runBelowDatabaseVersion: Int = 9

    @Suppress("DEPRECATION")
    override fun run(context: Context) {
        val oldFrameData = context.prefManager.currentSecondaryFramesWithDisplay

        // If there's nothing to convert, don't touch the string-display map. Running right after
        // the previous migration, `currentSecondaryFramesWithDisplay` is empty and the string map
        // already holds the converted data, so overwriting it here would drop the secondary frames.
        if (oldFrameData.isNotEmpty()) {
            val converted = oldFrameData.map { entry -> entry.key to "${entry.value}" }

            context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(
                context.prefManager.currentSecondaryFramesWithStringDisplay + converted,
            )

            context.prefManager.currentSecondaryFramesWithDisplay = hashMapOf()
        }
    }
}
