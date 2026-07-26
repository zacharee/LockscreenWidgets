@file:Suppress("unused")

package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import tk.zwander.common.data.WidgetData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.*
import tk.zwander.lockscreenwidgets.App

class FrameSpecificPreferences private constructor(
    val frameId: Int,
    private val context: Context,
) {
    val framePreferences: SharedPreferences = context.getSharedPreferences("frame_prefs_${frameId}", Context.MODE_PRIVATE)

    var currentWidgets: Set<WidgetData>
        get() = FramePrefs.getWidgetsForFrame(context, frameId)
        set(value) {
            FramePrefs.setWidgetsForFrame(context, frameId, value)
        }

    var rowCount: Int
        get() = getInt(PrefManager.KEY_FRAME_ROW_COUNT, 1)
        set(value) {
            putInt(PrefManager.KEY_FRAME_ROW_COUNT, value)
        }

    var colCount: Int
        get() = getInt(PrefManager.KEY_FRAME_COL_COUNT, 1)
        set(value) {
            putInt(PrefManager.KEY_FRAME_COL_COUNT, value)
        }

    var backgroundColor: Int
        get() = getInt(PrefManager.KEY_FRAME_BACKGROUND_COLOR, Color.TRANSPARENT)
        set(value) {
            putInt(PrefManager.KEY_FRAME_BACKGROUND_COLOR, value)
        }

    var blurBackground: Boolean
        get() = getBoolean(PrefManager.KEY_BLUR_BACKGROUND, false)
        set(value) {
            putBoolean(PrefManager.KEY_BLUR_BACKGROUND, value)
        }

    var blurBackgroundAmount: Int
        get() = getInt(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT, 100)
        set(value) {
            putInt(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT, value)
        }

    var maskedMode: Boolean
        get() = getBoolean(PrefManager.KEY_FRAME_MASKED_MODE, false)
        set(value) {
            putBoolean(PrefManager.KEY_FRAME_MASKED_MODE, value)
        }

    var maskedModeDimAmount: Float
        get() = getInt(PrefManager.KEY_MASKED_MODE_DIM_AMOUNT, 0) / 100f
        set(value) {
            putInt(PrefManager.KEY_MASKED_MODE_DIM_AMOUNT, (value * 100f).toInt())
        }

    var hideOnNotifications: Boolean
        get() = getBoolean(PrefManager.KEY_HIDE_ON_NOTIFICATIONS, false)
        set(value) {
            putBoolean(PrefManager.KEY_HIDE_ON_NOTIFICATIONS, value)
        }

    var hideOnNotificationShade: Boolean
        get() = getBoolean(PrefManager.KEY_HIDE_ON_NOTIFICATION_SHADE, false)
        set(value) {
            putBoolean(PrefManager.KEY_HIDE_ON_NOTIFICATION_SHADE, value)
        }

    var hideOnSecurityPage: Boolean
        get() = getBoolean(PrefManager.KEY_HIDE_ON_SECURITY_PAGE, true)
        set(value) {
            putBoolean(PrefManager.KEY_HIDE_ON_SECURITY_PAGE, value)
        }

    var hideOnFaceWidgets: Boolean
        get() = getBoolean(PrefManager.KEY_HIDE_ON_FACEWIDGETS, false)
        set(value) {
            putBoolean(PrefManager.KEY_HIDE_ON_FACEWIDGETS, value)
        }

    var hideWhenKeyboardShown: Boolean
        get() = getBoolean(PrefManager.KEY_FRAME_HIDE_WHEN_KEYBOARD_SHOWN, false)
        set(value) {
            putBoolean(PrefManager.KEY_FRAME_HIDE_WHEN_KEYBOARD_SHOWN, value)
        }

    var hideOnEdgePanel: Boolean
        get() = getBoolean(PrefManager.KEY_HIDE_ON_EDGE_PANEL, true)
        set(value) {
            putBoolean(PrefManager.KEY_HIDE_ON_EDGE_PANEL, value)
        }

    var showOnMainLockScreen: Boolean
        get() = getBoolean(PrefManager.KEY_SHOW_ON_MAIN_LOCK_SCREEN, true) || !showInNotificationShade
        set(value) {
            putBoolean(PrefManager.KEY_SHOW_ON_MAIN_LOCK_SCREEN, value)
        }

    var showInNotificationShade: Boolean
        get() = getBoolean(PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER, false)
        set(value) {
            putBoolean(PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER, value)
        }

    var separateLockNCPosition: Boolean
        get() = getBoolean(PrefManager.KEY_SEPARATE_POS_FOR_LOCK_NC, false)
        set(value) {
            putBoolean(PrefManager.KEY_SEPARATE_POS_FOR_LOCK_NC, value)
        }

    var ignoreWidgetTouches: Boolean
        get() = getBoolean(PrefManager.KEY_FRAME_IGNORE_WIDGET_TOUCHES, false)
        set(value) {
            putBoolean(PrefManager.KEY_FRAME_IGNORE_WIDGET_TOUCHES, value)
        }

    var ignoreAllTouches: Boolean
        get() = getBoolean(PrefManager.KEY_FRAME_IGNORE_TOUCHES, false)
        set(value) {
            putBoolean(PrefManager.KEY_FRAME_IGNORE_TOUCHES, value)
        }

    var currentIndex: Int
        get() = getInt(PrefManager.KEY_CURRENT_PAGE, 0)
        set(value) {
            putInt(PrefManager.KEY_CURRENT_PAGE, value)
        }

    var itemSpacingDp: Float
        get() = getInt(PrefManager.KEY_FRAME_ITEM_SPACING, 0) / 10f
        set(value) {
            putInt(PrefManager.KEY_FRAME_ITEM_SPACING, (value * 10f).toInt())
        }

    fun getString(key: String, def: String? = null): String? = framePreferences.getString(key, def)
    fun getFloat(key: String, def: Float): Float = framePreferences.getFloat(key, def)
    fun getInt(key: String, def: Int): Int = framePreferences.getInt(key, def)
    fun getBoolean(key: String, def: Boolean): Boolean = framePreferences.getBoolean(key, def)
    fun getStringSet(key: String, def: Set<String>): Set<String> = framePreferences.getStringSet(key, def)?.toSet() ?: def

    fun putString(key: String, value: String?) = framePreferences.edit(true) { putString(key, value) }
    fun putFloat(key: String, value: Float) = framePreferences.edit(true) { putFloat(key, value) }
    fun putInt(key: String, value: Int) = framePreferences.edit(true) { putInt(key, value) }
    fun putBoolean(key: String, value: Boolean) = framePreferences.edit(true) { putBoolean(key, value) }
    fun putStringSet(key: String, value: Set<String>) = framePreferences.edit(true) { putStringSet(key, value) }

    fun clear() {
        framePreferences.edit { clear() }
    }

    companion object {
        private val instances = mutableMapOf<Int, FrameSpecificPreferences>()

        operator fun get(frameId: Int): FrameSpecificPreferences {
            val context = App.instance

            return instances[frameId] ?: FrameSpecificPreferences(frameId, context).also {
                instances[frameId] = it
            }
        }

        fun all(context: Context): List<FrameSpecificPreferences> {
            return ([MainWidgetFrameDelegate.ID] + context.prefManager.currentSecondaryFramesWithStringDisplay.map { it.key }).map { frameId ->
                FrameSpecificPreferences[frameId]
            }
        }

        internal fun remove(frameId: Int): FrameSpecificPreferences? {
            return instances.remove(frameId)
        }

        @Deprecated("Frames have their own preference files now. This should only be used by the migration function.")
        fun keyFor(frameId: Int, baseKey: String): String {
            return FramePrefs.generatePrefKey(baseKey, frameId)
        }

        fun doAnyFramesHaveSettingEnabled(
            context: Context,
            baseKey: String,
            def: Boolean = false,
        ): Boolean {
            return all(context).any {
                it.getBoolean(baseKey, def)
            }
        }
    }
}

object FramePrefs {
    private const val KEY_FRAME_WIDGETS = "FRAME_WIDGETS_FOR_FRAME_"
    @Deprecated("Use PrefManager version")
    const val KEY_FRAME_ROW_COUNT = "FRAME_ROW_COUNT_FOR_FRAME_"
    @Deprecated("Use PrefManager version")
    const val KEY_FRAME_COL_COUNT = "FRAME_COL_COUNT_FOR_FRAME_"

    fun getWidgetsForFrame(context: Context, frameId: Int): Set<WidgetData> {
        if (frameId == MainWidgetFrameDelegate.ID) {
            return context.prefManager.currentWidgets
        }

        val stringVal = context.prefManager.getString(
            generatePrefKey(KEY_FRAME_WIDGETS, frameId),
            null,
        )

        return context.prefManager.gson.safeFromJson<LinkedHashSet<WidgetData>>(
            stringVal,
        ) ?: LinkedHashSet()
    }

    fun setWidgetsForFrame(context: Context, frameId: Int, widgets: Collection<WidgetData>) {
        val set = LinkedHashSet(widgets.toSet())

        if (frameId == MainWidgetFrameDelegate.ID) {
            context.prefManager.currentWidgets = set
            return
        }

        context.prefManager.putString(
            generatePrefKey(KEY_FRAME_WIDGETS, frameId),
            context.prefManager.gson.toJson(set),
        )
    }

    fun removeFrame(context: Context, frameId: Int) {
        if (frameId == MainWidgetFrameDelegate.ID) {
            return
        }

        context.prefManager.currentSecondaryFramesWithStringDisplay = context.prefManager.currentSecondaryFramesWithStringDisplay.apply {
            remove(frameId)
        }

        getWidgetsForFrame(context, frameId).forEach { data ->
            context.widgetHostCompat.deleteAppWidgetId(data.id)
        }
        context.prefManager.remove(generatePrefKey(KEY_FRAME_WIDGETS, frameId))

        [
            FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
            FrameSizeAndPosition.FrameType.SecondaryLockscreen.Landscape(frameId),
        ].forEach { type ->
            context.frameSizeAndPosition.removeSizeForType(type)
            context.frameSizeAndPosition.removePositionForType(type)
        }

        FrameSpecificPreferences[frameId].clear()
        // We shouldn't need to remove any frame preference instances since they don't hold their own state.
    }

    fun generateCurrentWidgetsKey(id: Int): String {
        if (id == MainWidgetFrameDelegate.ID) {
            return PrefManager.KEY_CURRENT_WIDGETS
        }

        return "${KEY_FRAME_WIDGETS}_${id}"
    }

    @Deprecated("Frames have their own preference files now.")
    fun generatePrefKey(baseKey: String, id: Int): String {
        if (id == MainWidgetFrameDelegate.ID) {
            return when (baseKey) {
                KEY_FRAME_COL_COUNT -> PrefManager.KEY_FRAME_COL_COUNT
                KEY_FRAME_ROW_COUNT -> PrefManager.KEY_FRAME_ROW_COUNT
                KEY_FRAME_WIDGETS -> PrefManager.KEY_CURRENT_WIDGETS
                else -> baseKey
            }
        }

        return "${baseKey}_${id}"
    }
}
