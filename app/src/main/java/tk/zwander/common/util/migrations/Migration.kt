package tk.zwander.common.util.migrations

import android.content.Context

interface Migration {
    val runBelowDatabaseVersion: Int

    fun run(context: Context)
}
