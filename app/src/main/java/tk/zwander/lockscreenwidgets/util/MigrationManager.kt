package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.util.migrations.AddExtraWidgetInfoMigration

class MigrationManager private constructor(context: Context) : ContextWrapper(context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: MigrationManager? = null

        fun getInstance(context: Context): MigrationManager {
            return instance ?: MigrationManager(context.safeApplicationContext).also {
                instance = it
            }
        }
    }

    private val migrations = listOf(
        AddExtraWidgetInfoMigration()
    )

    fun runMigrations() {
        val currentVersion = BuildConfig.DATABASE_VERSION
        val storedVersion = prefManager.databaseVersion

        if (currentVersion > storedVersion) {
            migrations.forEach { migration ->
                if (migration.runOnOrBelowDatabaseVersion >= storedVersion) {
                    migration.run(this)
                }
            }

            updateDatabaseVersion(currentVersion)
        }
    }

    private fun updateDatabaseVersion(newVersion: Int) {
        prefManager.databaseVersion = newVersion
    }
}