package tk.zwander.lockscreenwidgets.util.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import com.google.gson.reflect.TypeToken
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.PrefManager
import tk.zwander.lockscreenwidgets.util.logUtils
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.util.safeApplicationContext

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

    fun createBackupString(): String {
        val currentWidgets = prefManager.currentWidgetsString
        val rows = prefManager.frameRowCount
        val cols = prefManager.frameColCount

        val data = HashMap<String, String?>()
        data[PrefManager.KEY_CURRENT_WIDGETS] = currentWidgets
        data[PrefManager.KEY_FRAME_ROW_COUNT] = rows.toString()
        data[PrefManager.KEY_FRAME_COL_COUNT] = cols.toString()

        return prefManager.gson.toJson(data)
    }

    fun restoreBackupString(string: String?): Boolean {
        if (string.isNullOrBlank()) {
            logUtils.debugLog("Backup string is null.")
            return false
        }

        return try {
            val dataMap = prefManager.gson.fromJson<HashMap<String, String?>>(
                string,
                object : TypeToken<HashMap<String, String?>>(){}.type
            )

            handleDataMap(dataMap)
        } catch (e: Exception) {
            logUtils.debugLog("No data map. Trying old restore.", e)

            handleWidgetString(string)
        }
    }

    private fun handleDataMap(dataMap: HashMap<String, String?>): Boolean {
        if (dataMap.isNullOrEmpty()) {
            logUtils.debugLog("Backup data empty.")
            return false
        }

        val newWidgets = dataMap[PrefManager.KEY_CURRENT_WIDGETS]
        val rows = dataMap[PrefManager.KEY_FRAME_ROW_COUNT]
        val cols = dataMap[PrefManager.KEY_FRAME_COL_COUNT]

        return handleWidgetString(newWidgets).also {
            if (it) {
                rows?.toIntOrNull()?.let { prefManager.frameRowCount = it }
                cols?.toIntOrNull()?.let { prefManager.frameColCount = it }
            }
        }
    }

    private fun handleWidgetString(newWidgets: String?): Boolean {
        if (newWidgets.isNullOrBlank()) {
            logUtils.debugLog("Widget string is null.")
            return false
        }

        if (!isValidWidgetsString(newWidgets)) {
            logUtils.debugLog("Invalid widget string.")
            return false
        }

        val old = prefManager.currentWidgets
        val widgetHost = WidgetHostCompat.getInstance(this, 1003)

        old.forEach {
            widgetHost.deleteAppWidgetId(it.id)
        }

        prefManager.currentWidgetsString = newWidgets

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