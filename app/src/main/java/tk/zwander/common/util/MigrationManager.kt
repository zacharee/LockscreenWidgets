package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import dev.zwander.lswinterconnect.safeApplicationContext
import tk.zwander.common.util.migrations.*
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

    // Order matters! This list is intentionally not sorted by runBelowDatabaseVersion: the icon
    // migration reads the secondary-frame prefs that the two display migrations populate, so it
    // must stay after them. Do not reorder without checking those dependencies.
    private val migrations = [
        AddExtraWidgetInfoMigration(),
        WidgetSizeMigration(),
        FrameDimAmountMigration(),
        FrameSizeAndPositionMigration(),
        SecondaryFrameToFrameWithDisplayMigration(),
        SecondaryFrameWithDisplayToStringDisplayMigration(),
        WidgetIconMigration(),
        CurrentFrameIndexMigration(),
        FramePrefsMigration(),
    ]

    fun runMigrations() {
        val currentVersion = BuildConfig.DATABASE_VERSION
        val storedVersion = context.prefManager.databaseVersion

        // Nothing to do. This also avoids a synchronous prefs write on every cold start.
        if (currentVersion <= storedVersion) {
            return
        }

        context.logUtils.debugLog(
            message = "Running database migrations from version $storedVersion to $currentVersion",
            throwable = null,
        )

        migrations.forEach { migration ->
            if (migration.runBelowDatabaseVersion > storedVersion) {
                context.logUtils.debugLog(
                    message = "Running migration ${migration.javaClass.simpleName} " +
                        "(runs for versions below ${migration.runBelowDatabaseVersion})",
                    throwable = null,
                )
                migration.run(context)
            }
        }

        updateDatabaseVersion(currentVersion)
    }

    private fun updateDatabaseVersion(newVersion: Int) {
        context.prefManager.databaseVersion = newVersion
    }
}
