package tk.zwander.lockscreenwidgets.util

import android.annotation.IntegerRes
import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.WidgetData

class PrefManager private constructor(context: Context) : ContextWrapper(context) {
    companion object {
        const val KEY_CURRENT_WIDGETS = "current_widgets"
        const val KEY_FRAME_WIDTH = "frame_width"
        const val KEY_FRAME_HEIGHT = "frame_height"
        const val KEY_POS_X = "position_x"
        const val KEY_POS_Y = "position_y"
        const val KEY_FIRST_VIEWING = "first_viewing"
        const val KEY_FIRST_RUN = "first_run"
        const val KEY_OPAQUE_FRAME = "opaque_frame_background"
        const val KEY_HIDE_ON_NOTIFICATIONS = "hide_on_notifications"

        private var instance: PrefManager? = null

        fun getInstance(context: Context): PrefManager {
            return instance ?: run {
                instance = PrefManager(context.applicationContext)
                instance!!
            }
        }
    }

    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val gson = GsonBuilder()
        .create()

    var currentWidgets: HashSet<WidgetData>
        get() = gson.fromJson(
            getString(KEY_CURRENT_WIDGETS),
            object : TypeToken<HashSet<WidgetData>>() {}.type
        ) ?: HashSet()
        set(value) {
            putString(
                KEY_CURRENT_WIDGETS,
                gson.toJson(value)
            )
        }

    var frameWidthDp: Float
        get() = getFloat(KEY_FRAME_WIDTH, getResourceFloat(R.integer.def_frame_width))
        set(value) {
            putFloat(KEY_FRAME_WIDTH, value)
        }

    var frameHeightDp: Float
        get() = getFloat(KEY_FRAME_HEIGHT, getResourceFloat(R.integer.def_frame_height))
        set(value) {
            putFloat(KEY_FRAME_HEIGHT, value)
        }

    var posX: Int
        get() = getInt(KEY_POS_X, 0)
        set(value) {
            putInt(KEY_POS_X, value)
        }

    var posY: Int
        get() = getInt(KEY_POS_Y, 0)
        set(value) {
            putInt(KEY_POS_Y, value)
        }

    var firstViewing: Boolean
        get() = getBoolean(KEY_FIRST_VIEWING, true)
        set(value) {
            putBoolean(KEY_FIRST_VIEWING, value)
        }

    var firstRun: Boolean
        get() = getBoolean(KEY_FIRST_RUN, true)
        set(value) {
            putBoolean(KEY_FIRST_RUN, value)
        }

    var opaqueFrame: Boolean
        get() = getBoolean(KEY_OPAQUE_FRAME, false)
        set(value) {
            putBoolean(KEY_OPAQUE_FRAME, value)
        }

    var hideOnNotifications: Boolean
        get() = getBoolean(KEY_HIDE_ON_NOTIFICATIONS, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_NOTIFICATIONS, value)
        }

    fun getString(key: String, def: String? = null) = prefs.getString(key, def)
    fun getFloat(key: String, def: Float) = prefs.getFloat(key, def)
    fun getInt(key: String, def: Int) = prefs.getInt(key, def)
    fun getBoolean(key: String, def: Boolean) = prefs.getBoolean(key, def)

    fun putString(key: String, value: String?) = prefs.edit { putString(key, value) }
    fun putFloat(key: String, value: Float) = prefs.edit { putFloat(key, value) }
    fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }
    fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    fun getResourceFloat(@IntegerRes resource: Int): Float {
        return resources.getInteger(resource).toFloat()
    }
}