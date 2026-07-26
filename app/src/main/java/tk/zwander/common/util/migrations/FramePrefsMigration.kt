package tk.zwander.common.util.migrations

import android.content.Context
import androidx.core.content.edit
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences

class FramePrefsMigration : Migration {
    override val runBelowDatabaseVersion: Int = 10

    private fun createKeys(context: Context): List<FrameMigrationInfo> {
        return (context.prefManager.currentSecondaryFramesWithStringDisplay.keys + -1).map { frameId ->
            val framePrefs = FrameSpecificPreferences[frameId]

            FrameMigrationInfo(
                frameId = frameId,
                prefs = framePrefs,
                keysAndTypes = baseKeys.map { [baseKey, type] ->
                    FrameMigrationInfo.KeysAndType(
                        sourceKey = FrameSpecificPreferences.keyFor(frameId, baseKey),
                        destKey = when (baseKey) {
                            FramePrefs.KEY_FRAME_COL_COUNT -> PrefManager.KEY_FRAME_COL_COUNT
                            FramePrefs.KEY_FRAME_ROW_COUNT -> PrefManager.KEY_FRAME_ROW_COUNT
                            else -> baseKey
                        },
                        type = type,
                    )
                },
            )
        }
    }

    override fun run(context: Context) {
        val keysToMigrate = createKeys(context)

        keysToMigrate.forEach { (prefs, keysAndTypes) ->
            prefs.framePreferences.edit(true) {
                keysAndTypes.forEach { (sourceKey, destKey) ->
                    @Suppress("UNCHECKED_CAST")
                    when (val value = context.prefManager.prefs.all[sourceKey]) {
                        is Int -> putInt(destKey, value)
                        is Boolean -> putBoolean(destKey, value)
                        is String -> putString(destKey, value)
                        is Long -> putLong(destKey, value)
                        is Float -> putFloat(destKey, value)
                        is Set<*> -> putStringSet(destKey, value as Set<String?>)
                    }
                }
            }

            keysAndTypes.forEach { (sourceKey) ->
                context.prefManager.remove(sourceKey)
            }
        }
    }

    companion object {
        private val baseKeys = [
            PrefManager.KEY_FRAME_BACKGROUND_COLOR to Int,
            PrefManager.KEY_BLUR_BACKGROUND to Boolean,
            PrefManager.KEY_BLUR_BACKGROUND_AMOUNT to Int,
            PrefManager.KEY_FRAME_MASKED_MODE to Boolean,
            PrefManager.KEY_MASKED_MODE_DIM_AMOUNT to Int,
            PrefManager.KEY_SEPARATE_POS_FOR_LOCK_NC to Boolean,
            PrefManager.KEY_HIDE_ON_NOTIFICATIONS to Boolean,
            PrefManager.KEY_HIDE_ON_NOTIFICATION_SHADE to Boolean,
            PrefManager.KEY_HIDE_ON_SECURITY_PAGE to Boolean,
            PrefManager.KEY_HIDE_ON_FACEWIDGETS to Boolean,
            PrefManager.KEY_FRAME_HIDE_WHEN_KEYBOARD_SHOWN to Boolean,
            PrefManager.KEY_HIDE_ON_EDGE_PANEL to Boolean,
            PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER to Boolean,
            PrefManager.KEY_SHOW_ON_MAIN_LOCK_SCREEN to Boolean,
            PrefManager.KEY_FRAME_IGNORE_WIDGET_TOUCHES to Boolean,
            PrefManager.KEY_FRAME_IGNORE_TOUCHES to Boolean,
            FramePrefs.KEY_FRAME_ROW_COUNT to Int,
            FramePrefs.KEY_FRAME_COL_COUNT to Int,
        ]
    }

    data class FrameMigrationInfo(
        val frameId: Int,
        val prefs: FrameSpecificPreferences,
        val keysAndTypes: List<KeysAndType>,
    ) {
        data class KeysAndType(
            val sourceKey: String,
            val destKey: String,
            val type: Any,
        )
    }
}
