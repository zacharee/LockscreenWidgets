package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import tk.zwander.common.util.migrations.AddExtraWidgetInfoMigration
import tk.zwander.common.util.migrations.FrameDimAmountMigration
import tk.zwander.common.util.migrations.FrameSizeAndPositionMigration
import tk.zwander.common.util.migrations.WidgetIconMigration
import tk.zwander.common.util.migrations.WidgetSizeMigration
import tk.zwander.lockscreenwidgets.BuildConfig

val Context.migrationManager: MigrationManager
    get() = MigrationManager.getInstance(this)

class MigrationManager private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: MigrationManager? = null

        @Synchronized
        fun getInstance(context: Context): MigrationManager {
            return instance ?: MigrationManager(context.safeApplicationContext).also {
                instance = it
            }
        }
    }

    private val migrations = listOf(
        AddExtraWidgetInfoMigration(),
        WidgetSizeMigration(),
        FrameDimAmountMigration(),
        FrameSizeAndPositionMigration(),
        WidgetIconMigration(),
    )

    fun runMigrations() {
        val currentVersion = BuildConfig.DATABASE_VERSION
        val storedVersion = context.prefManager.databaseVersion

        if (currentVersion > storedVersion) {
            migrations.forEach { migration ->
                if (migration.runOnOrBelowDatabaseVersion >= storedVersion) {
                    migration.run(context)
                }
            }

            updateDatabaseVersion(currentVersion)
        }
    }

    private fun updateDatabaseVersion(newVersion: Int) {
        context.prefManager.databaseVersion = newVersion
    }
}