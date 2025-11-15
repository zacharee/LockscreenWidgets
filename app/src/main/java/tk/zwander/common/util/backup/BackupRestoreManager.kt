package tk.zwander.common.util.backup

import android.annotation.SuppressLint
import android.content.Context
import android.view.Display
import com.google.gson.reflect.TypeToken
import tk.zwander.common.data.WidgetData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.safeApplicationContext
import tk.zwander.lockscreenwidgets.util.FramePrefs

val Context.backupRestoreManager: BackupRestoreManager
    get() = BackupRestoreManager.getInstance(this)

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
        DRAWER
    }

    fun createBackupString(which: Which): String {
        val currentWidgets = when (which) {
            Which.FRAME -> context.prefManager.currentWidgetsString
            Which.DRAWER -> context.prefManager.drawerWidgetsString
        }
        val rows = when (which) {
            Which.FRAME -> context.prefManager.frameRowCount
            Which.DRAWER -> Int.MAX_VALUE
        }
        val cols = when (which) {
            Which.FRAME -> context.prefManager.frameColCount
            Which.DRAWER -> context.prefManager.drawerColCount
        }

        val secondaryFrames = if (which == Which.FRAME) context.prefManager.currentSecondaryFramesWithStringDisplay else null
        val frameWidgetsMap =
            secondaryFrames?.entries?.associate { it.toPair() to FramePrefs.getWidgetsForFrame(context, it.key) }
        val frameGridsMap = secondaryFrames?.entries?.associate {
            it.toPair() to FramePrefs.getGridSizeForFrame(context, it.key)
        }

        val data = HashMap<String, String?>()
        data[PrefManager.KEY_CURRENT_WIDGETS] = currentWidgets
        data[PrefManager.KEY_FRAME_ROW_COUNT] = rows.toString()
        data[PrefManager.KEY_FRAME_COL_COUNT] = cols.toString()

        if (secondaryFrames != null) {
            data["secondaryFramesNewest"] = context.prefManager.gson.toJson(secondaryFrames)
            data["frameWidgetsMapNew"] = context.prefManager.gson.toJson(frameWidgetsMap)
            data["frameGridsMapNew"] = context.prefManager.gson.toJson(frameGridsMap)
        }

        return context.prefManager.gson.toJson(data)
    }

    fun restoreBackupString(string: String?, which: Which): Boolean {
        if (string.isNullOrBlank()) {
            context.logUtils.debugLog("Backup string is null.")
            return false
        }

        return try {
            val dataMap = context.prefManager.gson.fromJson<HashMap<String, String?>>(
                string,
                object : TypeToken<HashMap<String, String?>>(){}.type,
            )

            handleDataMap(dataMap, which)
        } catch (e: Exception) {
            context.logUtils.debugLog("No data map. Trying old restore.", e)

            handleWidgetString(string, which)
        }
    }

    private fun handleDataMap(dataMap: HashMap<String, String?>, which: Which): Boolean {
        if (dataMap.isEmpty()) {
            context.logUtils.debugLog("Backup data empty.")
            return false
        }

        val newWidgets = dataMap[PrefManager.KEY_CURRENT_WIDGETS]
        val rows = dataMap[PrefManager.KEY_FRAME_ROW_COUNT]
        val cols = dataMap[PrefManager.KEY_FRAME_COL_COUNT]

        if (which == Which.FRAME) {
            val secondaryFramesOld = dataMap["secondaryFrames"]?.let {
                context.prefManager.gson.fromJson(it, object : TypeToken<ArrayList<Int>>() {})
            }
            val secondaryFramesNew = dataMap["secondaryFramesNew"]?.let {
                context.prefManager.gson.fromJson(it, object : TypeToken<HashMap<Int, Int>>() {})
            }
            val secondaryFramesNewest = dataMap["secondaryFramesNewest"]?.let {
                context.prefManager.gson.fromJson(it, object : TypeToken<HashMap<Int, String>>() {})
            }
            val frameWidgetsMap = dataMap["frameWidgetsMap"]?.let {
                context.prefManager.gson.fromJson(it, object : TypeToken<HashMap<Int, LinkedHashSet<WidgetData>>>() {})
            }
            val frameWidgetsMapNew = dataMap["frameWidgetsMapNew"]?.let {
                context.prefManager.gson.fromJson(it, object : TypeToken<HashMap<Pair<Int, Int>, HashSet<WidgetData>>>() {})
            }
            val frameGridsMap = dataMap["frameGridsMap"]?.let {
                context.prefManager.gson.fromJson(it, object : TypeToken<HashMap<Int, Pair<Int, Int>>>() {})
            }
            val frameGridsMapNew = dataMap["frameGridsMapNew"]?.let {
                context.prefManager.gson.fromJson(it, object : TypeToken<HashMap<Pair<Int, Int>, Pair<Int, Int>>>() {})
            }

            secondaryFramesOld?.let {
                context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(
                    it.associateWith {
                        "${Display.DEFAULT_DISPLAY}"
                    },
                )
            }
            // We don't want to respect the backup's saved displays because they might not
            // match the device's current displays.
            secondaryFramesNew?.let {
                context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(
                    it.map { (key, _) ->
                        key to "${Display.DEFAULT_DISPLAY}"
                    }.toMap(),
                )
            }
            // We don't want to respect the backup's saved displays because they might not
            // match the device's current displays.
            secondaryFramesNewest?.let {
                context.prefManager.currentSecondaryFramesWithStringDisplay = HashMap(
                    it.map { (key, _) ->
                        key to "${Display.DEFAULT_DISPLAY}"
                    }.toMap(),
                )
            }

            frameWidgetsMap?.forEach { (id, widgets) ->
                FramePrefs.setWidgetsForFrame(context, id, widgets)
            }
            frameWidgetsMapNew?.forEach { (frame, widgets) ->
                FramePrefs.setWidgetsForFrame(context, frame.first, widgets)
            }

            frameGridsMap?.forEach { (id, grid) ->
                FramePrefs.setRowCountForFrame(context, id, grid.first)
                FramePrefs.setColCountForFrame(context, id, grid.second)
            }
            frameGridsMapNew?.forEach { (frame, grid) ->
                FramePrefs.setRowCountForFrame(context, frame.first, grid.first)
                FramePrefs.setColCountForFrame(context, frame.first, grid.second)
            }
        }

        return handleWidgetString(newWidgets, which).also {
            if (it) {
                rows?.toIntOrNull()?.let { rows ->
                    when (which) {
                        Which.FRAME -> context.prefManager.frameRowCount = rows
                        Which.DRAWER -> {}
                    }
                }
                cols?.toIntOrNull()?.let { cols ->
                    when (which) {
                        Which.FRAME -> context.prefManager.frameColCount = cols
                        Which.DRAWER -> context.prefManager.drawerColCount = cols
                    }
                }
            }
        }
    }

    private fun handleWidgetString(newWidgets: String?, which: Which): Boolean {
        if (newWidgets.isNullOrBlank()) {
            context.logUtils.debugLog("Widget string is null.")
            return false
        }

        if (!isValidWidgetsString(newWidgets)) {
            context.logUtils.debugLog("Invalid widget string.")
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

    private fun isValidWidgetsString(string: String?): Boolean {
        return try {
            context.prefManager.gson.fromJson<LinkedHashSet<WidgetData>>(
                string,
                object : TypeToken<LinkedHashSet<WidgetData>>() {}.type,
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