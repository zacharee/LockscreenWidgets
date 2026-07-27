package tk.zwander.common.util.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.Display
import androidx.core.content.edit
import dev.zwander.lswinterconnect.safeApplicationContext
import tk.zwander.common.data.WidgetData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.*
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

val Context.backupRestoreManager: BackupRestoreManager
    get() = BackupRestoreManager.getInstance(this)

data class AllFramesBackup(
    val frameData: List<FrameBackupHolder>,
    val globalPrefsMap: HashMap<String, Any?>,
)

data class FrameBackupHolder(
    val frameId: Int,
    val data: IndividualBackupData,
)

data class IndividualBackupData(
    val widgets: LinkedHashSet<WidgetData>,
    val prefsMap: HashMap<String, Any?>,
)

class BackupRestoreManager private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: BackupRestoreManager? = null

        @Synchronized
        fun getInstance(context: Context): BackupRestoreManager {
            return instance ?: BackupRestoreManager(context.safeApplicationContext).apply {
                instance = this
            }
        }
    }

    enum class Which {
        FRAME,
        DRAWER;
    }

    // TODO: migrate drawer prefs to their own file.
    fun createDrawerBackupString(): String {
        val currentWidgets = context.prefManager.drawerWidgets

        return context.prefManager.gson.toJson(
            IndividualBackupData(
                widgets = currentWidgets,
                prefsMap = HashMap(
                    [
                        PrefManager.KEY_ANIMATE_DRAWER_SHOW_HIDE,
                        PrefManager.KEY_DRAWER_ANIMATION_DURATION,
                        PrefManager.KEY_CLOSE_DRAWER_ON_EMPTY_TAP,
                        PrefManager.KEY_DOUBLE_TAP_EMPTY_DRAWER_SPACE_TURN_OFF_DISPLAY,
                        PrefManager.KEY_LOCK_WIDGET_DRAWER,
                        PrefManager.KEY_REQUEST_UNLOCK_DRAWER,
                        PrefManager.KEY_DRAWER_DIRECTLY_CHECK_FOR_ACTIVITY,
                        PrefManager.KEY_DRAWER_FORCE_RELOAD_WIDGETS,
                        PrefManager.KEY_DRAWER_HIDE_WHEN_NOTIFICATION_PANEL_OPEN,
                        PrefManager.KEY_BLUR_DRAWER_BACKGROUND,
                        PrefManager.KEY_BLUR_DRAWER_BACKGROUND_AMOUNT,
                        PrefManager.KEY_BLUR_DRAWER_STATUS_BAR_AREA,
                        PrefManager.KEY_DRAWER_BACKGROUND_COLOR,
                        PrefManager.KEY_DRAWER_BACKGROUND_OVER_STATUS_BAR,
                        PrefManager.KEY_DRAWER_COL_COUNT,
                        PrefManager.KEY_DRAWER_ITEM_SPACING,
                        PrefManager.KEY_DRAWER_SIDE_PADDING,
                        PrefManager.KEY_DRAWER_WIDGET_CORNER_RADIUS,
                        PrefManager.KEY_SHOW_DRAWER_HANDLE,
                        PrefManager.KEY_SHOW_DRAWER_HANDLE_ONLY_WHEN_LOCKED,
                        PrefManager.KEY_SHOW_DRAWER_HANDLE_SHADOW,
                        PrefManager.KEY_DRAWER_HANDLE_TAP_TO_OPEN,
                        PrefManager.KEY_DRAWER_HANDLE_LOCK_POSITION,
                        PrefManager.KEY_DRAWER_HANDLE_HEIGHT,
                        PrefManager.KEY_DRAWER_HANDLE_WIDTH,
                        PrefManager.KEY_DRAWER_HANDLE_COLOR,
                    ].associateWith { key ->
                        context.prefManager.prefs.all[key]
                    },
                ),
            ),
        )
    }

    // TODO: migrate remaining frame prefs to their own file.
    fun createFrameBackupString(): String {
        val frameBackups = (context.prefManager.currentSecondaryFramesWithStringDisplay.keys + MainWidgetFrameDelegate.ID).map { frameId ->
            val framePrefs = FrameSpecificPreferences[frameId]
            val prefsMap = framePrefs.framePreferences.all
            val currentWidgets = framePrefs.currentWidgets

            FrameBackupHolder(
                frameId = frameId,
                data = IndividualBackupData(
                    widgets = LinkedHashSet(currentWidgets),
                    prefsMap = HashMap(prefsMap),
                ),
            )
        }

        val backup = AllFramesBackup(
            frameData = frameBackups,
            globalPrefsMap = HashMap(
                [
                    PrefManager.KEY_FRAME_CORNER_RADIUS,
                    PrefManager.KEY_FRAME_WIDGET_CORNER_RADIUS,
                    PrefManager.KEY_LOCK_WIDGET_FRAME,
                    PrefManager.KEY_SEPARATE_LAYOUT_FOR_LANDSCAPE,
                    PrefManager.KEY_HIDE_IN_LANDSCAPE,
                    PrefManager.KEY_FRAME_REMEMBER_POSITION,
                    PrefManager.KEY_DRAWER_FORCE_RELOAD_WIDGETS,
                    PrefManager.KEY_ANIMATE_DRAWER_SHOW_HIDE,
                    PrefManager.KEY_ANIMATION_DURATION,
                    PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR,
                    PrefManager.KEY_DOUBLE_TAP_EMPTY_FRAME_SPACE_TURN_OFF_DISPLAY,
                    PrefManager.KEY_TOUCH_PROTECTION,
                    PrefManager.KEY_REQUEST_UNLOCK,
                    PrefManager.KEY_FRAME_DIRECTLY_CHECK_FOR_ACTIVITY,
                ].associateWith { key ->
                    context.prefManager.prefs.all[key]
                },
            ),
        )

        return context.prefManager.gson.toJson(backup)
    }

    fun restoreBackup(string: String, which: Which): Boolean {
        try {
            val backup = context.prefManager.gson.safeFromJson<AllFramesBackup>(string)
                ?: throw NullPointerException("Unable to parse JSON")
            handleFramesRestore(backup)

            return true
        } catch (e: Throwable) {
            context.logUtils.normalLog("Backup string is not AllFramesBackup", e)
        }

        try {
            val backup = context.prefManager.gson.safeFromJson<FrameBackupHolder>(string)
                ?: throw NullPointerException("Unable to parse JSON")
            handleIndividualFrameRestore(backup)

            return true
        } catch (e: Throwable) {
            context.logUtils.normalLog("Backup string is not FrameBackupHolder", e)
        }

        try {
            val backup = context.prefManager.gson.safeFromJson<IndividualBackupData>(string)
                ?: throw NullPointerException("Unable to parse JSON")
            handleDrawerRestore(backup)

            return true
        } catch (e: Throwable) {
            context.logUtils.normalLog("Backup string is not FrameBackupHolder", e)
        }

        return LegacyRestore.restoreBackupString(context, string, which)
    }

    fun handleFramesRestore(data: AllFramesBackup) {
        context.prefManager.prefs.edit(true) {
            putMapInPreferences(data.globalPrefsMap)
        }

        data.frameData.forEach { frame ->
            handleIndividualFrameRestore(frame)
        }
    }

    fun handleIndividualFrameRestore(data: FrameBackupHolder) {
        val framePrefs = FrameSpecificPreferences[data.frameId]
        framePrefs.currentWidgets = data.data.widgets

        framePrefs.framePreferences.edit(true) {
            putMapInPreferences(data.data.prefsMap)
        }
    }

    fun handleDrawerRestore(data: IndividualBackupData) {
        context.prefManager.prefs.edit(true) {
            context.prefManager.drawerWidgets = data.widgets
            putMapInPreferences(data.prefsMap)
        }
    }

    fun SharedPreferences.Editor.putMapInPreferences(map: Map<String, Any?>) {
        map.forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is String -> putString(key, value)
                is Set<*> -> putStringSet(key, value as Set<String>)
                is Int -> putInt(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
            }
        }
    }

    object LegacyRestore {
        fun restoreBackupString(context: Context, string: String?, which: Which): Boolean {
            if (string.isNullOrBlank()) {
                context.logUtils.debugLog("Backup string is null.")
                return false
            }

            return try {
                val dataMap = context.prefManager.gson.mapFromJson<String, Any?>(
                    string,
                )

                handleDataMap(context, dataMap, which)
            } catch (e: Exception) {
                context.logUtils.normalLog("No data map. Trying old restore.", e)

                if (!isValidWidgetsString(context, string)) {
                    return false
                }

                handleWidgetString(context, string, which)
            }
        }

        private fun handleDataMap(context: Context, dataMap: HashMap<String, Any?>, which: Which): Boolean {
            if (dataMap.isEmpty()) {
                context.logUtils.debugLog("Backup data empty.")
                return false
            }

            val newWidgets = dataMap[PrefManager.KEY_CURRENT_WIDGETS].toString()
            val rows = dataMap[PrefManager.KEY_FRAME_ROW_COUNT]?.toString()?.toIntOrNull()
            val cols = dataMap[PrefManager.KEY_FRAME_COL_COUNT]?.toString()?.toIntOrNull()

            if (which == Which.FRAME) {
                val secondaryFramesOld = dataMap["secondaryFrames"]?.let {
                    context.prefManager.gson.safeFromJson<ArrayList<Int>>(it.toString())
                }
                val secondaryFramesNew = dataMap["secondaryFramesNew"]?.let {
                    context.prefManager.gson.mapFromJson<Int, Int>(it.toString())
                }
                val secondaryFramesNewest = dataMap["secondaryFramesNewest"]?.let {
                    context.prefManager.gson.mapFromJson<Int, String>(it.toString())
                }
                val frameWidgetsMap = dataMap["frameWidgetsMap"]?.let {
                    context.prefManager.gson.mapFromJson<Int, LinkedHashSet<WidgetData>>(it.toString())
                }
                val frameWidgetsMapNew = dataMap["frameWidgetsMapNew"]?.let {
                    context.prefManager.gson.mapFromJson<Pair<Int, String>, HashSet<WidgetData>>(it.toString())
                }
                val frameGridsMap = dataMap["frameGridsMap"]?.let {
                    context.prefManager.gson.mapFromJson<Int, Pair<Int, Int>>(it.toString())
                }
                val frameGridsMapNew = dataMap["frameGridsMapNew"]?.let {
                    context.prefManager.gson.mapFromJson<Pair<Int, String>, Pair<Int, Int>>(it.toString())
                }

                secondaryFramesOld?.let { frameId ->
                    context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(
                        frameId.associateWith {
                            "${Display.DEFAULT_DISPLAY}"
                        },
                    )
                }
                // We don't want to respect the backup's saved displays because they might not
                // match the device's current displays.
                secondaryFramesNew?.let {
                    context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(
                        it.map { (key) ->
                            key to "${Display.DEFAULT_DISPLAY}"
                        }.toMap(),
                    )
                }
                // We don't want to respect the backup's saved displays because they might not
                // match the device's current displays.
                secondaryFramesNewest?.let {
                    context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(
                        it.map { (key) ->
                            key to "${Display.DEFAULT_DISPLAY}"
                        }.toMap(),
                    )
                }

                frameWidgetsMap?.forEach { [id, widgets] ->
                    FramePrefs.setWidgetsForFrame(context, id, widgets)
                }
                frameWidgetsMapNew?.forEach { [frame, widgets] ->
                    FramePrefs.setWidgetsForFrame(context, frame.first, widgets)
                }

                frameGridsMap?.forEach { [id, grid] ->
                    FrameSpecificPreferences[id].apply {
                        rowCount = grid.first
                        colCount = grid.second
                    }
                }
                frameGridsMapNew?.forEach { [frame, grid] ->
                    FrameSpecificPreferences[frame.first].apply {
                        rowCount = grid.first
                        colCount = grid.second
                    }
                }
            }

            return handleWidgetString(context, newWidgets, which).also {
                if (it) {
                    rows?.let { rows ->
                        when (which) {
                            Which.FRAME -> {
                                FrameSpecificPreferences[MainWidgetFrameDelegate.ID].rowCount = rows
                            }
                            Which.DRAWER -> {}
                        }
                    }
                    cols?.let { cols ->
                        when (which) {
                            Which.FRAME -> {
                                FrameSpecificPreferences[MainWidgetFrameDelegate.ID].colCount = cols
                            }
                            Which.DRAWER -> context.prefManager.drawerColCount = cols
                        }
                    }
                }
            }
        }

        private fun handleWidgetString(context: Context, newWidgets: String?, which: Which): Boolean {
            if (newWidgets.isNullOrBlank()) {
                context.logUtils.debugLog("Widget string is null.")
                return false
            }

            val old = when (which) {
                Which.FRAME -> context.prefManager.currentWidgets
                Which.DRAWER -> context.prefManager.drawerWidgets
            }
            val widgetHost = context.widgetHostCompat

            old.forEach {
                widgetHost.deleteAppWidgetId(it.id)
            }

            when (which) {
                Which.FRAME -> context.prefManager.currentWidgetsString = newWidgets
                Which.DRAWER -> context.prefManager.drawerWidgetsString = newWidgets
            }

            return true
        }

        private fun isValidWidgetsString(context: Context, string: String?): Boolean {
            return try {
                context.prefManager.gson.safeFromJson<LinkedHashSet<WidgetData>>(
                    string,
                ) != null
            } catch (e: Exception) {
                try {
                    context.logUtils.normalLog("Error parsing input string $string", e)
                } catch (e2: OutOfMemoryError) {
                    context.logUtils.normalLog("Error parsing input string. Input is too large.", e2)
                }
                false
            }
        }
    }
}
