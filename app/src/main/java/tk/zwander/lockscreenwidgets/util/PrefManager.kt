package tk.zwander.lockscreenwidgets.util

import android.annotation.IntegerRes
import android.content.Context
import android.content.ContextWrapper
import androidx.collection.ArraySet
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
        const val KEY_OPACITY_MODE = "opacity_mode"
        const val KEY_HIDE_ON_NOTIFICATIONS = "hide_on_notifications"
        const val KEY_WIDGET_FRAME_ENABLED = "widget_frame_enabled"
        const val KEY_PAGE_INDICATOR_BEHAVIOR = "page_indicator_behavior"
        const val KEY_HIDE_ON_SECURITY_PAGE = "hide_on_security_page"
        const val KEY_HIDE_ON_NOTIFICATION_SHADE = "hide_on_notification_shade"
        const val KEY_ANIMATE_SHOW_HIDE = "animate_show_hide"
        const val KEY_CURRENT_PAGE = "current_page"
        const val KEY_DEBUG_LOG = "debug_log"
        const val KEY_FRAME_COL_COUNT = "frame_col_count"
        const val KEY_FRAME_ROW_COUNT = "frame_row_count"

        const val VALUE_PAGE_INDICATOR_BEHAVIOR_HIDDEN = 0
        const val VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE = 1
        const val VALUE_PAGE_INDICATOR_BEHAVIOR_SHOWN = 2

        const val VALUE_OPACITY_MODE_TRANSPARENT = 0
        const val VALUE_OPACITY_MODE_MASKED = 1
        const val VALUE_OPACITY_MODE_OPAQUE = 2

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

    var currentWidgets: LinkedHashSet<WidgetData>
        get() = gson.fromJson(
            getString(KEY_CURRENT_WIDGETS),
            object : TypeToken<LinkedHashSet<WidgetData>>() {}.type
        ) ?: LinkedHashSet()
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

    var currentPage: Int
        get() = getInt(KEY_CURRENT_PAGE, 0)
        set(value) {
            putInt(KEY_CURRENT_PAGE, value)
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

    var hideOnNotifications: Boolean
        get() = getBoolean(KEY_HIDE_ON_NOTIFICATIONS, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_NOTIFICATIONS, value)
        }

    var widgetFrameEnabled: Boolean
        get() = getBoolean(KEY_WIDGET_FRAME_ENABLED, true)
        set(value) {
            putBoolean(KEY_WIDGET_FRAME_ENABLED, value)
        }

    var hideOnSecurityPage: Boolean
        get() = getBoolean(KEY_HIDE_ON_SECURITY_PAGE, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_SECURITY_PAGE, value)
        }

    var hideOnNotificationShade: Boolean
        get() = getBoolean(KEY_HIDE_ON_NOTIFICATION_SHADE, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_NOTIFICATION_SHADE, value)
        }

    var animateShowHide: Boolean
        get() = getBoolean(KEY_ANIMATE_SHOW_HIDE, true)
        set(value) {
            putBoolean(KEY_ANIMATE_SHOW_HIDE, value)
        }

    var debugLog: Boolean
        get() = getBoolean(KEY_DEBUG_LOG, false)
        set(value) {
            putBoolean(KEY_DEBUG_LOG, value)
        }

    var pageIndicatorBehavior: Int
        get() = getString(KEY_PAGE_INDICATOR_BEHAVIOR, VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE.toString())!!.toInt()
        set(value) {
            putString(KEY_PAGE_INDICATOR_BEHAVIOR, value.toString())
        }

    var opacityMode: Int
        get() = getString(KEY_OPACITY_MODE, VALUE_OPACITY_MODE_TRANSPARENT.toString())!!.toInt()
        set(value) {
            putString(KEY_OPACITY_MODE, value.toString())
        }

    var frameColCount: Int
        get() = getInt(KEY_FRAME_COL_COUNT, 1)
        set(value) {
            putInt(KEY_FRAME_COL_COUNT, value)
        }

    var frameRowCount: Int
        get() = getInt(KEY_FRAME_ROW_COUNT, 1)
        set(value) {
            putInt(KEY_FRAME_ROW_COUNT, value)
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