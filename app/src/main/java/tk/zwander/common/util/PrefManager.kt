package tk.zwander.common.util

import android.annotation.IntegerRes
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.data.WidgetTileInfo
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.Mode

//Convenience method for getting the preference store instance
val Context.prefManager: PrefManager
    get() = PrefManager.getInstance(this)

fun Context.calculateNCPosXFromRightDefault(): Int {
    val fromRight = dpAsPx(resources.getInteger(R.integer.def_notification_pos_x_from_right_dp))
    val screenWidth = screenSize.x
    val frameWidthPx = dpAsPx(prefManager.notificationFrameWidthDp)

    val frameRight = (frameWidthPx / 2f)
    val coord = (screenWidth / 2f) - fromRight - frameRight

    return coord.toInt()
}

fun Context.calculateNCPosYFromTopDefault(): Int {
    val fromTop = dpAsPx(resources.getInteger(R.integer.def_notification_pos_y_from_top_dp))
    val screenHeight = screenSize.y
    val frameHeightPx = dpAsPx(prefManager.notificationFrameHeightDp)

    val frameTop = (frameHeightPx / 2f)
    val coord = -(screenHeight / 2f) + frameTop + fromTop

    return coord.toInt()
}

/**
 * Handle data persistence.
 */
class PrefManager private constructor(context: Context) : ContextWrapper(context) {
    companion object {
        const val KEY_CURRENT_WIDGETS = "current_widgets"
        const val KEY_FRAME_WIDTH = "frame_width"
        const val KEY_NOTIFICATION_FRAME_WIDTH = "notification_frame_width"
        const val KEY_FRAME_HEIGHT = "frame_height"
        const val KEY_NOTIFICATION_FRAME_HEIGHT = "notification_frame_height"
        const val KEY_POS_X = "position_x"
        const val KEY_NOTIFICATION_POS_X = "notification_position_x"
        const val KEY_LOCK_NOTIFICATION_POS_X = "lock_notification_position_x"
        const val KEY_POS_Y = "position_y"
        const val KEY_NOTIFICATION_POS_Y = "notification_position_y"
        const val KEY_LOCK_NOTIFICATION_POS_Y = "lock_notification_position_y"
        const val KEY_FIRST_VIEWING = "first_viewing"
        const val KEY_FIRST_RUN = "first_run"
        const val KEY_HIDE_ON_NOTIFICATIONS = "hide_on_notifications"
        const val KEY_WIDGET_FRAME_ENABLED = "widget_frame_enabled"
        const val KEY_PAGE_INDICATOR_BEHAVIOR = "page_indicator_behavior"
        const val KEY_HIDE_ON_SECURITY_PAGE = "hide_on_security_page"
        const val KEY_HIDE_ON_NOTIFICATION_SHADE = "hide_on_notification_shade"
        const val KEY_ANIMATE_SHOW_HIDE = "animate_show_hide"
        const val KEY_ANIMATION_DURATION = "animation_duration"
        const val KEY_CURRENT_PAGE = "current_page"
        const val KEY_DEBUG_LOG = "debug_log"
        const val KEY_FRAME_COL_COUNT = "frame_col_count"
        const val KEY_FRAME_ROW_COUNT = "frame_row_count"
        const val KEY_SHOW_DEBUG_ID_VIEW = "show_debug_id_view"
        const val KEY_ACCESSIBILITY_EVENT_DELAY = "accessibility_event_delay"
        const val KEY_PRESENT_IDS = "present_ids"
        const val KEY_NON_PRESENT_IDS = "non_present_ids"
        const val KEY_WIDGET_SIZES = "widget_sizes"
        const val KEY_SHOW_IN_NOTIFICATION_CENTER = "show_in_notification_center"
        const val KEY_SHOW_ON_MAIN_LOCK_SCREEN = "show_on_main_lock_screen"
        const val KEY_FRAME_CORNER_RADIUS = "corner_radius"
        const val KEY_FRAME_BACKGROUND_COLOR = "background_color"
        const val KEY_FRAME_MASKED_MODE = "masked_mode"
        const val KEY_TOUCH_PROTECTION = "touch_protection"
        const val KEY_REQUEST_UNLOCK = "request_unlock"
        const val KEY_REQUEST_UNLOCK_DRAWER = "request_unlock_drawer"
        const val KEY_HIDE_ON_FACEWIDGETS = "hide_on_facewidgets"
        const val KEY_HIDE_IN_LANDSCAPE = "hide_in_landscape"
        const val KEY_SEPARATE_POS_FOR_LOCK_NC = "separate_position_for_lock_notification"
        const val KEY_PREVIEW_POS_X = "preview_position_x"
        const val KEY_PREVIEW_POS_Y = "preview_position_y"
        const val KEY_PREVIEW_WIDTH = "preview_width"
        const val KEY_PREVIEW_HEIGHT = "preview_height"
        const val KEY_CUSTOM_TILES = "custom_tiles"
        const val KEY_SHORTCUT_IDS = "shortcut_ids"
        const val KEY_LOCK_WIDGET_FRAME = "lock_widget_frame"
        const val KEY_LOCK_WIDGET_DRAWER = "lock_widget_drawer"
        const val KEY_DATABASE_VERSION = "database_version"
        const val KEY_BLUR_BACKGROUND = "blur_background"
        const val KEY_BLUR_DRAWER_BACKGROUND = "blur_drawer_background"
        const val KEY_BLUR_BACKGROUND_AMOUNT = "blur_background_amount"
        const val KEY_BLUR_DRAWER_BACKGROUND_AMOUNT = "blur_drawer_background_amount"
        const val KEY_MASKED_MODE_DIM_AMOUNT = "masked_mode_wallpaper_dim_amount"
        const val KEY_DRAWER_WIDGETS = "drawer_widgets"
        const val KEY_DRAWER_COL_COUNT = "drawer_col_count"
        const val KEY_CLOSE_DRAWER_ON_EMPTY_TAP = "close_drawer_on_empty_tap"
        const val KEY_SHOW_DRAWER_HANDLE = "show_drawer_handle"
        const val KEY_DRAWER_HANDLE_HEIGHT = "drawer_handle_height"
        const val KEY_DRAWER_HANDLE_WIDTH = "drawer_handle_width"
        const val KEY_DRAWER_HANDLE_Y_VALUE = "drawer_handle_y_value"
        const val KEY_DRAWER_HANDLE_SIDE = "drawer_handle_side"
        const val KEY_DRAWER_HANDLE_COLOR = "drawer_handle_color"
        const val KEY_SHOW_DRAWER_HANDLE_SHADOW = "show_drawer_handle_shadow"
        const val KEY_DRAWER_ENABLED = "drawer_enabled"
        const val KEY_DRAWER_BACKGROUND_COLOR = "drawer_background_color"
        const val KEY_FRAME_WIDGET_CORNER_RADIUS = "frame_widget_corner_radius"
        const val KEY_DRAWER_WIDGET_CORNER_RADIUS = "drawer_widget_corner_radius"
        const val KEY_FRAME_REMEMBER_POSITION = "frame_remember_position"
        const val KEY_DRAWER_SIDE_PADDING = "drawer_side_padding"
        const val KEY_HIDE_ON_EDGE_PANEL = "frame_hide_on_edge_panel"

        const val VALUE_PAGE_INDICATOR_BEHAVIOR_HIDDEN = 0
        const val VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE = 1
        const val VALUE_PAGE_INDICATOR_BEHAVIOR_SHOWN = 2

        private var instance: PrefManager? = null

        @Synchronized
        fun getInstance(context: Context): PrefManager {
            return instance ?: PrefManager(context.safeApplicationContext).apply {
                instance = this
            }
        }
    }

    //The actual SharedPreferences implementation
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    val gson: Gson = GsonBuilder()
        .setExclusionStrategies(CrashFixExclusionStrategy())
        .registerTypeAdapter(Uri::class.java, GsonUriHandler())
        .registerTypeAdapter(Intent::class.java, GsonIntentHandler())
        .create()

    //The widgets currently added to the widget frame
    var currentWidgets: LinkedHashSet<WidgetData>
        get() = gson.fromJson(
            currentWidgetsString,
            object : TypeToken<LinkedHashSet<WidgetData>>() {}.type
        ) ?: LinkedHashSet()
        set(value) {
            currentWidgetsString = gson.toJson(value)
        }

    var currentWidgetsString: String?
        get() = getString(KEY_CURRENT_WIDGETS)
        set(value) {
            putString(KEY_CURRENT_WIDGETS, value)
        }

    //The widgets currently added to the widget drawer
    var drawerWidgets: LinkedHashSet<WidgetData>
        get() = gson.fromJson(
            drawerWidgetsString,
            object : TypeToken<LinkedHashSet<WidgetData>>() {}.type
        ) ?: LinkedHashSet()
        set(value) {
            drawerWidgetsString = gson.toJson(value)
        }

    var drawerWidgetsString: String?
        get() = getString(KEY_DRAWER_WIDGETS)
        set(value) {
            putString(KEY_DRAWER_WIDGETS, value)
        }

    //The shortcuts currently added to the widget frame
    var shortcutIds: LinkedHashSet<String>
        get() = gson.fromJson(
            getString(KEY_SHORTCUT_IDS),
            object : TypeToken<LinkedHashSet<String>>() {}.type
        ) ?: LinkedHashSet()
        set(value) {
            putString(
                KEY_SHORTCUT_IDS,
                gson.toJson(value)
            )
        }

    //IDs the user has selected. The widget frame will hide if any of these are detected on-screen.
    var presentIds: HashSet<String>
        get() = HashSet(getStringSet(KEY_PRESENT_IDS, HashSet()))
        set(value) {
            putStringSet(
                KEY_PRESENT_IDS,
                value)
        }

    //IDs the user has selected. The widget frame will hide if any of these are *not* detected on-screen.
    var nonPresentIds: HashSet<String>
        get() = HashSet(getStringSet(KEY_NON_PRESENT_IDS, HashSet()))
        set(value) {
            putStringSet(
                KEY_NON_PRESENT_IDS,
                value
            )
        }

    //If any widgets have custom sizes, those sizes are stored here.
    @Deprecated("Widget sizes are stored inline with the widget info.")
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

    //The width of the frame in the NC in DP.
    var notificationFrameWidthDp: Float
        get() = getFloat(KEY_NOTIFICATION_FRAME_WIDTH, getResourceFloat(R.integer.def_notification_frame_width))
        set(value) {
            putFloat(KEY_NOTIFICATION_FRAME_WIDTH, value)
        }

    //The width of the frame in preview mode in DP
    var previewWidthDp: Float
        get() = getFloat(KEY_PREVIEW_WIDTH, getResourceFloat(R.integer.def_frame_width))
        set(value) {
            putFloat(KEY_PREVIEW_WIDTH, value)
        }

    //The height of the frame in DP
    var frameHeightDp: Float
        get() = getFloat(KEY_FRAME_HEIGHT, getResourceFloat(R.integer.def_frame_height))
        set(value) {
            putFloat(KEY_FRAME_HEIGHT, value)
        }

    //The height of the frame in the NC in DP.
    var notificationFrameHeightDp: Float
        get() = getFloat(KEY_NOTIFICATION_FRAME_HEIGHT, getResourceFloat(R.integer.def_notification_frame_height))
        set(value) {
            putFloat(KEY_NOTIFICATION_FRAME_HEIGHT, value)
        }

    var previewHeightDp: Float
        get() = getFloat(KEY_PREVIEW_HEIGHT, getResourceFloat(R.integer.def_frame_height))
        set(value) {
            putFloat(KEY_PREVIEW_HEIGHT, value)
        }

    //The horizontal position of the center of the frame (from the center of the screen) in pixels
    var posX: Int
        get() = getInt(KEY_POS_X, 0)
        set(value) {
            putInt(KEY_POS_X, value)
        }

    //The horizontal position of the center of the frame in the NC (from the center of the screen) in pixels
    var notificationPosX: Int
        get() = getInt(KEY_NOTIFICATION_POS_X, calculateNCPosXFromRightDefault())
        set(value) {
            putInt(KEY_NOTIFICATION_POS_X, value)
        }

    //The horizontal position of the center of the frame in the locked NC (from the center of the screen) in pixels
    var lockNotificationPosX: Int
        get() = getInt(KEY_LOCK_NOTIFICATION_POS_X, calculateNCPosXFromRightDefault())
        set(value) {
            putInt(KEY_LOCK_NOTIFICATION_POS_X, value)
        }

    //The horizontal position of the center of the frame in preview mode in pixels
    var previewPosX: Int
        get() = getInt(KEY_PREVIEW_POS_X, 0)
        set(value) {
            putInt(KEY_PREVIEW_POS_X, value)
        }

    //The vertical position of the center of the frame (from the center of the screen) in pixels
    var posY: Int
        get() = getInt(KEY_POS_Y, 0)
        set(value) {
            putInt(KEY_POS_Y, value)
        }

    //The vertical position of the center of the frame in the NC (from the center of the screen) in pixels
    var notificationPosY: Int
        get() = getInt(KEY_NOTIFICATION_POS_Y, calculateNCPosYFromTopDefault())
        set(value) {
            putInt(KEY_NOTIFICATION_POS_Y, value)
        }

    //The vertical position of the center of the frame in the NC (from the center of the screen) in pixels
    var lockNotificationPosY: Int
        get() = getInt(KEY_LOCK_NOTIFICATION_POS_Y, calculateNCPosYFromTopDefault())
        set(value) {
            putInt(KEY_LOCK_NOTIFICATION_POS_Y, value)
        }

    //The vertical position of the center of the frame in preview mode in pixels.
    var previewPosY: Int
        get() = getInt(KEY_PREVIEW_POS_Y, 0)
        set(value) {
            putInt(KEY_PREVIEW_POS_Y, value)
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

    //The widgets assigned to custom QS tiles.
    var customTiles: HashMap<Int, WidgetTileInfo>
        get() = gson.fromJson(
            getString(KEY_CUSTOM_TILES, null),
            object : TypeToken<HashMap<Int, WidgetTileInfo>>(){}.type
        ) ?: HashMap()
        set(value) {
            putString(
                KEY_CUSTOM_TILES,
                gson.toJson(value)
            )
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
        get() = getBoolean(KEY_WIDGET_FRAME_ENABLED, false)
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

    //Whether the widget frame should use a separate position in the notification center
    //when locked.
    var separatePosForLockNC: Boolean
        get() = getBoolean(KEY_SEPARATE_POS_FOR_LOCK_NC, false)
        set(value) {
            putBoolean(KEY_SEPARATE_POS_FOR_LOCK_NC, value)
        }

    //How the page indicator (scrollbar) should behave (always show, fade out on inactivity, never show).
    var pageIndicatorBehavior: Int
        get() {
            val defaultValue = VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE
            return getString(KEY_PAGE_INDICATOR_BEHAVIOR, defaultValue.toString())?.toInt() ?: defaultValue
        }
        set(value) {
            putString(KEY_PAGE_INDICATOR_BEHAVIOR, value.toString())
        }

    //The background color of the widget frame.
    var backgroundColor: Int
        get() = getInt(KEY_FRAME_BACKGROUND_COLOR, Color.TRANSPARENT)
        set(value) {
            putInt(KEY_FRAME_BACKGROUND_COLOR, value)
        }

    //Whether masked mode is enabled.
    //On compatible devices with a proper wallpaper setup,
    //this will emulate a transparent widget background by
    //drawing the user's wallpaper as the frame background.
    var maskedMode: Boolean
        get() = getBoolean(KEY_FRAME_MASKED_MODE, false)
        set(value) {
            putBoolean(KEY_FRAME_MASKED_MODE, value)
        }

    //Whether the frame background should be blurred.
    var blurBackground: Boolean
        get() = getBoolean(KEY_BLUR_BACKGROUND, false)
        set(value) {
            putBoolean(KEY_BLUR_BACKGROUND, value)
        }

    var blurDrawerBackground: Boolean
        get() = getBoolean(KEY_BLUR_DRAWER_BACKGROUND, false)
        set(value) {
            putBoolean(KEY_BLUR_DRAWER_BACKGROUND, value)
        }

    //How many columns of widgets should be shown per page.
    var frameColCount: Int
        get() = getInt(KEY_FRAME_COL_COUNT, 1)
        set(value) {
            putInt(KEY_FRAME_COL_COUNT, value)
        }

    var drawerColCount: Int
        get() = getInt(KEY_DRAWER_COL_COUNT, 2)
        set(value) {
            putInt(KEY_FRAME_COL_COUNT, value)
        }

    //How many rows of widgets should be shown per page.
    var frameRowCount: Int
        get() = getInt(KEY_FRAME_ROW_COUNT, 1)
        set(value) {
            putInt(KEY_FRAME_ROW_COUNT, value)
        }

    //The degree of the background blur
    var backgroundBlurAmount: Int
        get() = getInt(KEY_BLUR_BACKGROUND_AMOUNT, 100)
        set(value) {
            putInt(KEY_BLUR_BACKGROUND_AMOUNT, value)
        }

    var drawerBackgroundBlurAmount: Int
        get() = getInt(KEY_BLUR_DRAWER_BACKGROUND_AMOUNT, 100)
        set(value) {
            putInt(KEY_BLUR_DRAWER_BACKGROUND_AMOUNT, value)
        }

    //How much to dim the masked mode wallpaper (in percent)
    var wallpaperDimAmount: Float
        get() = getInt(KEY_MASKED_MODE_DIM_AMOUNT, 0) / 100f
        set(value) {
            putInt(KEY_MASKED_MODE_DIM_AMOUNT, (value * 100f).toInt())
        }

    //Whether to show in the notification center.
    //This mode has separate dimensions and positioning
    //vs the standard lock screen mode.
    //Only available for Samsung One UI 1.0 and later.
    var showInNotificationCenter: Boolean
        get() = getBoolean(KEY_SHOW_IN_NOTIFICATION_CENTER, false) && isOneUI
        set(value) {
            putBoolean(KEY_SHOW_IN_NOTIFICATION_CENTER, value)
        }

    //A dependent option for [showInNotificationCenter].
    //Disabling this while [showInNotificationCenter] is enabled
    //will cause the widget frame to only show in the notification center.
    var showOnMainLockScreen: Boolean
        get() = getBoolean(KEY_SHOW_ON_MAIN_LOCK_SCREEN, true) || !showInNotificationCenter
        set(value) {
            putBoolean(KEY_SHOW_ON_MAIN_LOCK_SCREEN, value)
        }

    //The corner radius for the widget frame
    //(how rounded the corners are, in dp)
    var cornerRadiusDp: Float
        get() = getInt(KEY_FRAME_CORNER_RADIUS, resources.getInteger(R.integer.def_corner_radius_dp_scaled_10x)) / 10f
        set(value) {
            putInt(KEY_FRAME_CORNER_RADIUS, (value * 10f).toInt())
        }

    var frameWidgetCornerRadiusDp: Float
        get() = getInt(KEY_FRAME_WIDGET_CORNER_RADIUS, resources.getInteger(R.integer.def_corner_radius_dp_scaled_10x)) / 10f
        set(value) {
            putInt(KEY_FRAME_WIDGET_CORNER_RADIUS, (value * 10f).toInt())
        }

    var drawerWidgetCornerRadiusDp: Float
        get() = getInt(KEY_DRAWER_WIDGET_CORNER_RADIUS, resources.getInteger(R.integer.def_corner_radius_dp_scaled_10x)) / 10f
        set(value) {
            putInt(KEY_DRAWER_WIDGET_CORNER_RADIUS, (value * 10f).toInt())
        }

    //Whether to enable touch protection
    //(ignore touches when proximity sensor
    //is covered)
    var touchProtection: Boolean
        get() = getBoolean(KEY_TOUCH_PROTECTION, false)
        set(value) {
            putBoolean(KEY_TOUCH_PROTECTION, value)
        }

    //Whether to request an unlock/dismiss the
    //notification center when LSWidg detects
    //an Activity launch.
    var requestUnlock: Boolean
        get() = getBoolean(KEY_REQUEST_UNLOCK, true)
        set(value) {
            putBoolean(KEY_REQUEST_UNLOCK, value)
        }

    var requestUnlockDrawer: Boolean
        get() = getBoolean(KEY_REQUEST_UNLOCK_DRAWER, true)
        set(value) {
            putBoolean(KEY_REQUEST_UNLOCK_DRAWER, value)
        }

    //Whether to hide the frame when Samsung's FaceWidgets screen is showing.
    //(One UI 3.0+)
    var hideOnFaceWidgets: Boolean
        get() = getBoolean(KEY_HIDE_ON_FACEWIDGETS, false)
        set(value) {
            putBoolean(KEY_HIDE_ON_FACEWIDGETS, value)
        }

    //Whether to hide the frame in landscape.
    var hideInLandscape: Boolean
        get() = getBoolean(KEY_HIDE_IN_LANDSCAPE, false)
        set(value) {
            putBoolean(KEY_HIDE_IN_LANDSCAPE, value)
        }

    //Whether to lock the frame layout and position.
    var lockWidgetFrame: Boolean
        get() = getBoolean(KEY_LOCK_WIDGET_FRAME, false)
        set(value) {
            putBoolean(KEY_LOCK_WIDGET_FRAME, value)
        }

    var lockWidgetDrawer: Boolean
        get() = getBoolean(KEY_LOCK_WIDGET_DRAWER, false)
        set(value) {
            putBoolean(KEY_LOCK_WIDGET_DRAWER, value)
        }

    //The duration of the fade-in/out animation.
    var animationDuration: Int
        get() = getInt(KEY_ANIMATION_DURATION, 300)
        set(value) {
            putInt(KEY_ANIMATION_DURATION, value)
        }

    //The current widget database version. Used for migrations.
    var databaseVersion: Int
        get() = getInt(KEY_DATABASE_VERSION, BuildConfig.DATABASE_VERSION)
        set(value) {
            putInt(KEY_DATABASE_VERSION, value)
        }

    var closeOnEmptyTap: Boolean
        get() = getBoolean(KEY_CLOSE_DRAWER_ON_EMPTY_TAP, false)
        set(value) {
            putBoolean(KEY_CLOSE_DRAWER_ON_EMPTY_TAP, value)
        }

    var showDrawerHandle: Boolean
        get() = getBoolean(KEY_SHOW_DRAWER_HANDLE, false)
        set(value) {
            putBoolean(KEY_SHOW_DRAWER_HANDLE, value)
        }

    var hideOnEdgePanel: Boolean
        get() = getBoolean(KEY_HIDE_ON_EDGE_PANEL, true)
        set(value) {
            putBoolean(KEY_HIDE_ON_EDGE_PANEL, value)
        }

    var drawerHandleHeight: Int
        get() = getInt(KEY_DRAWER_HANDLE_HEIGHT, 140)
        set(value) {
            putInt(KEY_DRAWER_HANDLE_HEIGHT, value)
        }

    var drawerHandleWidth: Int
        get() = getInt(KEY_DRAWER_HANDLE_WIDTH, 6)
        set(value) {
            putInt(KEY_DRAWER_HANDLE_WIDTH, value)
        }

    var drawerHandleYPosition: Int
        get() = getInt(KEY_DRAWER_HANDLE_Y_VALUE, 0)
        set(value) {
            putInt(KEY_DRAWER_HANDLE_Y_VALUE, value)
        }

    var drawerHandleSide: Int
        get() = getInt(KEY_DRAWER_HANDLE_SIDE, Gravity.RIGHT)
        set(value) {
            putInt(KEY_DRAWER_HANDLE_SIDE, value)
        }

    var drawerHandleColor: Int
        get() = getInt(KEY_DRAWER_HANDLE_COLOR, Color.WHITE)
        set(value) {
            putInt(KEY_DRAWER_HANDLE_COLOR, value)
        }

    var drawerHandleShadow: Boolean
        get() = getBoolean(KEY_SHOW_DRAWER_HANDLE_SHADOW, false)
        set(value) {
            putBoolean(KEY_SHOW_DRAWER_HANDLE_SHADOW, value)
        }

    var drawerEnabled: Boolean
        get() = getBoolean(KEY_DRAWER_ENABLED, false)
        set(value) {
            putBoolean(KEY_DRAWER_ENABLED, value)
        }

    var drawerBackgroundColor: Int
        get() = getInt(KEY_DRAWER_BACKGROUND_COLOR, ResourcesCompat.getColor(resources, R.color.drawerBackgroundDefault, theme))
        set(value) {
            putInt(KEY_DRAWER_BACKGROUND_COLOR, value)
        }

    var rememberFramePosition: Boolean
        get() = getBoolean(KEY_FRAME_REMEMBER_POSITION, true)
        set(value) {
            putBoolean(KEY_FRAME_REMEMBER_POSITION, value)
        }

    var drawerSidePadding: Float
        get() = getInt(KEY_DRAWER_SIDE_PADDING, 0) / 10f
        set(value) {
            putInt(KEY_DRAWER_SIDE_PADDING, (value * 10f).toInt())
        }

    fun getCorrectFrameWidth(mode: Mode): Float {
        return when (mode) {
            Mode.LOCK_NORMAL -> frameWidthDp
            Mode.LOCK_NOTIFICATION, Mode.NOTIFICATION -> notificationFrameWidthDp
            Mode.PREVIEW -> previewWidthDp
        }
    }

    fun setCorrectFrameWidth(mode: Mode, width: Float) {
        when (mode) {
            Mode.LOCK_NORMAL -> frameWidthDp = width
            Mode.NOTIFICATION, Mode.LOCK_NOTIFICATION -> notificationFrameWidthDp = width
            Mode.PREVIEW -> previewWidthDp = width
        }
    }

    fun getCorrectFrameHeight(mode: Mode): Float {
        return when (mode) {
            Mode.LOCK_NORMAL -> frameHeightDp
            Mode.LOCK_NOTIFICATION, Mode.NOTIFICATION -> notificationFrameHeightDp
            Mode.PREVIEW -> previewHeightDp
        }
    }

    fun setCorrectFrameHeight(mode: Mode, height: Float) {
        when (mode) {
            Mode.LOCK_NORMAL -> frameHeightDp = height
            Mode.NOTIFICATION, Mode.LOCK_NOTIFICATION -> notificationFrameHeightDp = height
            Mode.PREVIEW -> previewHeightDp = height
        }
    }

    fun getCorrectFrameX(mode: Mode): Int {
        return when (mode) {
            Mode.LOCK_NORMAL -> posX
            Mode.LOCK_NOTIFICATION -> lockNotificationPosX
            Mode.NOTIFICATION -> notificationPosX
            Mode.PREVIEW -> previewPosX
        }
    }

    fun setCorrectFrameX(mode: Mode, x: Int) {
        when (mode) {
            Mode.LOCK_NORMAL -> posX = x
            Mode.LOCK_NOTIFICATION -> lockNotificationPosX = x
            Mode.NOTIFICATION -> notificationPosX = x
            Mode.PREVIEW -> previewPosX = x
        }
    }

    fun setCorrectFramePos(mode: Mode, x: Int, y: Int) {
        prefs.edit {
            when (mode) {
                Mode.LOCK_NORMAL -> {
                    putInt(KEY_POS_X, x)
                    putInt(KEY_POS_Y, y)
                }
                Mode.LOCK_NOTIFICATION -> {
                    putInt(KEY_LOCK_NOTIFICATION_POS_X, x)
                    putInt(KEY_LOCK_NOTIFICATION_POS_Y, y)
                }
                Mode.NOTIFICATION -> {
                    putInt(KEY_NOTIFICATION_POS_X, x)
                    putInt(KEY_NOTIFICATION_POS_Y, y)
                }
                Mode.PREVIEW -> {
                    putInt(KEY_PREVIEW_POS_X, x)
                    putInt(KEY_PREVIEW_POS_Y, y)
                }
            }
        }
    }

    fun getCorrectFrameY(mode: Mode): Int {
        return when (mode) {
            Mode.LOCK_NORMAL -> posY
            Mode.LOCK_NOTIFICATION -> lockNotificationPosY
            Mode.NOTIFICATION -> notificationPosY
            Mode.PREVIEW -> previewPosY
        }
    }

    fun setCorrectFrameY(mode: Mode, y: Int) {
        when (mode) {
            Mode.LOCK_NORMAL -> posY = y
            Mode.LOCK_NOTIFICATION -> lockNotificationPosY = y
            Mode.NOTIFICATION -> notificationPosY = y
            Mode.PREVIEW -> previewPosY = y
        }
    }

    fun isValidWidgetsString(string: String?): Boolean {
        return try {
            gson.fromJson<LinkedHashSet<WidgetData>>(
                string,
                object : TypeToken<LinkedHashSet<WidgetData>>() {}.type
            ) != null
        } catch (e: Exception) {
            logUtils.normalLog("Error parsing input string $string", e)
            false
        }
    }

    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getString(key: String, def: String? = null): String? = prefs.getString(key, def)
    fun getFloat(key: String, def: Float): Float = prefs.getFloat(key, def)
    fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
    fun getBoolean(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun getStringSet(key: String, def: Set<String>): Set<String> = prefs.getStringSet(key, def)

    fun putString(key: String, value: String?) = prefs.edit(true) { putString(key, value) }
    fun putFloat(key: String, value: Float) = prefs.edit(true) { putFloat(key, value) }
    fun putInt(key: String, value: Int) = prefs.edit(true) { putInt(key, value) }
    fun putBoolean(key: String, value: Boolean) = prefs.edit(true) { putBoolean(key, value) }
    fun putStringSet(key: String, value: Set<String>) = prefs.edit(true) { putStringSet(key, value) }

    fun getResourceFloat(@IntegerRes resource: Int): Float {
        return resources.getInteger(resource).toFloat()
    }
}