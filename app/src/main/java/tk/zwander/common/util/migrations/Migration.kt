package tk.zwander.common.util.migrations

import android.content.Context

interface Migration {
    val runOnOrBelowDatabaseVersion: Int

    fun run(context: Context)
}