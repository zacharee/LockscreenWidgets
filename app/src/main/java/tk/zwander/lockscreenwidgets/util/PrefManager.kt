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
import tk.zwander.lockscreenwidgets.data.WidgetSizeData

/**
 * Handle data persistence.
 */
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
        const val KEY_SHOW_DEBUG_ID_VIEW = "show_debug_id_view"
        const val KEY_ACCESSIBILITY_EVENT_DELAY = "accessibility_event_delay"
        const val KEY_PRESENT_IDS = "present_ids"
        const val KEY_NON_PRESENT_IDS = "non_present_ids"
        const val KEY_WIDGET_SIZES = "widget_sizes"

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

    //The actual SharedPreferences implementation
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val gson = GsonBuilder()
        .create()

    //The widgets currently added to the widget frame
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

    //IDs the user has selected. The widget frame will hide if any of these are detected on-screen.
    var presentIds: HashSet<String>
        get() = HashSet(getStringSet(KEY_PRESENT_IDS, HashSet())!!)
        set(value) {
            putStringSet(
                KEY_PRESENT_IDS,
                value)
        }

    //IDs the user has selected. The widget frame will hide if any of these are *not* detected on-screen.
    var nonPresentIds: HashSet<String>
        get() = HashSet(getStringSet(KEY_NON_PRESENT_IDS, HashSet())!!)
        set(value) {
            putStringSet(
                KEY_NON_PRESENT_IDS,
                value
            )
        }

    //If any widgets have custom sizes, those sizes are stored here.
    var widgetSizes: HashMap<Int, WidgetSizeData>
        get() = gson.fromJson(
            getString(KEY_WIDGET_SIZES),
            object : TypeToken<HashMap<Int, WidgetSizeData>>() {}.type
        ) ?: HashMap()
        set(value) {
            putString(
                KEY_WIDGET_SIZES,
                gson.toJson(value)
            )
        }

    //The width of the frame in DP
    var frameWidthDp: Float
        get() = getFloat(KEY_FRAME_WIDTH, getResourceFloat(R.integer.def_frame_width))
        set(value) {
            putFloat(KEY_FRAME_WIDTH, value)
        }

    //The height of the frame in DP
    var frameHeightDp: Float
        get() = getFloat(KEY_FRAME_HEIGHT, getResourceFloat(R.integer.def_frame_height))
        set(value) {
            putFloat(KEY_FRAME_HEIGHT, value)
        }

    //The horizontal position of the center of the frame (from the center of the screen) in pixels
    var posX: Int
        get() = getInt(KEY_POS_X, 0)
        set(value) {
            putInt(KEY_POS_X, value)
        }

    //The vertical position of the center of the frame (from the center of the screen) in pixels
    var posY: Int
        get() = getInt(KEY_POS_Y, 0)
        set(value) {
            putInt(KEY_POS_Y, value)
        }

    //The current page/index of the frame the user is currently on. Stored value may be higher
    //than the last index of the current widgets, so make sure to guard against that.
    var currentPage: Int
        get() = getInt(KEY_CURRENT_PAGE, 0)
        set(value) {
            putInt(KEY_CURRENT_PAGE, value)
        }

    //The timeout between Accessibility events being reported to Lockscreen Widgets.
    //Lower values mean faster responses, at the cost of battery life and reliability.
    var accessibilityEventDelay: Int
        get() = getInt(KEY_ACCESSIBILITY_EVENT_DELAY, 50)
        set(value) {
            putInt(KEY_ACCESSIBILITY_EVENT_DELAY, value)
        }

    //If it's the first time the user's seeing the widget frame. Used to decide
    //if the interaction hint should be shown.
    var firstViewing: Boolean
        get() = getBoolean(KEY_FIRST_VIEWING, true)
        set(value) {
            putBoolean(KEY_FIRST_VIEWING, value)
        }

    //If it's the first time the user's running the app. Used to decided if
    //the full intro sequence should be shown.
    var firstRun: Boolean
        get() = getBoolean(KEY_FIRST_RUN, true)
        set(value) {
            putBoolean(KEY_FIRST_RUN, value)
        }

    //Whether or not the widget frame should hide when there are > min priority
    //notifications shown.
    var hideOnNotifications: Boolean
        get() = getBoolean(KEY_HIDE_ON_NOTIFICATIONS, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_NOTIFICATIONS, value)
        }

    //Whether the widget frame is actually enabled.
    var widgetFrameEnabled: Boolean
        get() = getBoolean(KEY_WIDGET_FRAME_ENABLED, true)
        set(value) {
            putBoolean(KEY_WIDGET_FRAME_ENABLED, value)
        }

    //Whether the widget frame should hide on the password/pin/fingerprint/pattern
    //input screen.
    var hideOnSecurityPage: Boolean
        get() = getBoolean(KEY_HIDE_ON_SECURITY_PAGE, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_SECURITY_PAGE, value)
        }

    //Whether the widget frame should hide when the notification shade is down.
    var hideOnNotificationShade: Boolean
        get() = getBoolean(KEY_HIDE_ON_NOTIFICATION_SHADE, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_NOTIFICATION_SHADE, value)
        }

    //Whether the widget frame should animate its hide/show sequences.
    var animateShowHide: Boolean
        get() = getBoolean(KEY_ANIMATE_SHOW_HIDE, true)
        set(value) {
            putBoolean(KEY_ANIMATE_SHOW_HIDE, value)
        }

    //Whether to create verbose logs for debugging purposes.
    var debugLog: Boolean
        get() = getBoolean(KEY_DEBUG_LOG, false)
        set(value) {
            putBoolean(KEY_DEBUG_LOG, value)
        }

    //Whether to show the debug ID list overlay on the widget frame.
    //Only true if debugLog is true.
    var showDebugIdView: Boolean
        get() = getBoolean(KEY_SHOW_DEBUG_ID_VIEW, false) && debugLog
        set(value) {
            putBoolean(KEY_SHOW_DEBUG_ID_VIEW, value)
        }

    //How the page indicator (scrollbar) should behave (always show, fade out on inactivity, never show).
    var pageIndicatorBehavior: Int
        get() = getString(KEY_PAGE_INDICATOR_BEHAVIOR, VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE.toString())!!.toInt()
        set(value) {
            putString(KEY_PAGE_INDICATOR_BEHAVIOR, value.toString())
        }

    //The appearance of the widget frame's background (transparent, opaque, masked).
    //Masked mode attempts to reconstruct the relevant portion of the user's wallpaper as the
    //frame background, giving it the effect of transparency, with the ability to overlay
    //lock screen elements.
    var opacityMode: Int
        get() = getString(KEY_OPACITY_MODE, VALUE_OPACITY_MODE_TRANSPARENT.toString())!!.toInt()
        set(value) {
            putString(KEY_OPACITY_MODE, value.toString())
        }

    //How many columns of widgets should be shown per page.
    var frameColCount: Int
        get() = getInt(KEY_FRAME_COL_COUNT, 1)
        set(value) {
            putInt(KEY_FRAME_COL_COUNT, value)
        }

    //How many rows of widgets should be shown per page.
    var frameRowCount: Int
        get() = getInt(KEY_FRAME_ROW_COUNT, 1)
        set(value) {
            putInt(KEY_FRAME_ROW_COUNT, value)
        }

    fun getString(key: String, def: String? = null) = prefs.getString(key, def)
    fun getFloat(key: String, def: Float) = prefs.getFloat(key, def)
    fun getInt(key: String, def: Int) = prefs.getInt(key, def)
    fun getBoolean(key: String, def: Boolean) = prefs.getBoolean(key, def)
    fun getStringSet(key: String, def: Set<String>) = prefs.getStringSet(key, def)

    fun putString(key: String, value: String?) = prefs.edit { putString(key, value) }
    fun putFloat(key: String, value: Float) = prefs.edit { putFloat(key, value) }
    fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }
    fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }
    fun putStringSet(key: String, value: Set<String>) = prefs.edit { putStringSet(key, value) }

    fun getResourceFloat(@IntegerRes resource: Int): Float {
        return resources.getInteger(resource).toFloat()
    }
}