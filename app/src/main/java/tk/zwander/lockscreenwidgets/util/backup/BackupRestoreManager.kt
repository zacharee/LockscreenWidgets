package tk.zwander.lockscreenwidgets.util.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import com.google.gson.reflect.TypeToken
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.host.widgetHostCompat
import tk.zwander.lockscreenwidgets.util.PrefManager
import tk.zwander.lockscreenwidgets.util.logUtils
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.util.safeApplicationContext

val Context.backupRestoreManager: BackupRestoreManager
    get() = BackupRestoreManager.getInstance(this)

class BackupRestoreManager private constructor(context: Context) : ContextWrapper(context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: BackupRestoreManager? = null

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
            Which.FRAME -> prefManager.currentWidgetsString
            Which.DRAWER -> prefManager.drawerWidgetsString
        }
        val rows = when (which) {
            Which.FRAME -> prefManager.frameRowCount
            Which.DRAWER -> Int.MAX_VALUE
        }
        val cols = when (which) {
            Which.FRAME -> prefManager.frameColCount
            Which.DRAWER -> prefManager.drawerColCount
        }

        val data = HashMap<String, String?>()
        data[PrefManager.KEY_CURRENT_WIDGETS] = currentWidgets
        data[PrefManager.KEY_FRAME_ROW_COUNT] = rows.toString()
        data[PrefManager.KEY_FRAME_COL_COUNT] = cols.toString()

        return prefManager.gson.toJson(data)
    }

    fun restoreBackupString(string: String?, which: Which): Boolean {
        if (string.isNullOrBlank()) {
            logUtils.debugLog("Backup string is null.")
            return false
        }

        return try {
            val dataMap = prefManager.gson.fromJson<HashMap<String, String?>>(
                string,
                object : TypeToken<HashMap<String, String?>>(){}.type
            )

            handleDataMap(dataMap, which)
        } catch (e: Exception) {
            logUtils.debugLog("No data map. Trying old restore.", e)

            handleWidgetString(string, which)
        }
    }

    private fun handleDataMap(dataMap: HashMap<String, String?>, which: Which): Boolean {
        if (dataMap.isEmpty()) {
            logUtils.debugLog("Backup data empty.")
            return false
        }

        val newWidgets = dataMap[PrefManager.KEY_CURRENT_WIDGETS]
        val rows = dataMap[PrefManager.KEY_FRAME_ROW_COUNT]
        val cols = dataMap[PrefManager.KEY_FRAME_COL_COUNT]

        return handleWidgetString(newWidgets, which).also {
            if (it) {
                rows?.toIntOrNull()?.let { rows ->
                    when (which) {
                        Which.FRAME -> prefManager.frameRowCount = rows
                        Which.DRAWER -> {}
                    }
                }
                cols?.toIntOrNull()?.let { cols ->
                    when (which) {
                        Which.FRAME -> prefManager.frameColCount = cols
                        Which.DRAWER -> prefManager.drawerColCount = cols
                    }
                }
            }
        }
    }

    private fun handleWidgetString(newWidgets: String?, which: Which): Boolean {
        if (newWidgets.isNullOrBlank()) {
            logUtils.debugLog("Widget string is null.")
            return false
        }

        if (!isValidWidgetsString(newWidgets)) {
            logUtils.debugLog("Invalid widget string.")
            return false
        }

        val old = when (which) {
            Which.FRAME -> prefManager.currentWidgets
            Which.DRAWER -> prefManager.drawerWidgets
        }
        val widgetHost = widgetHostCompat

        old.forEach {
            widgetHost.deleteAppWidgetId(it.id)
        }

        when (which) {
            Which.FRAME -> prefManager.currentWidgetsString = newWidgets
            Which.DRAWER -> prefManager.drawerWidgetsString = newWidgets
        }

        return true
    }

    private fun isValidWidgetsString(string: String?): Boolean {
        return try {
            prefManager.gson.fromJson<LinkedHashSet<WidgetData>>(
                string,
                object : TypeToken<LinkedHashSet<WidgetData>>() {}.type
            ) != null
        } catch (e: Exception) {
            logUtils.normalLog("Error parsing input string $string", e)
            false
        }
    }
}