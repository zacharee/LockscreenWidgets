@file:Suppress("unused")

package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.graphics.Color
import com.google.gson.reflect.TypeToken
import tk.zwander.common.data.WidgetData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.prefManager

class FrameSpecificPreferences(
    val frameId: Int,
    private val context: Context,
) {
    var currentWidgets: Set<WidgetData>
        get() = FramePrefs.getWidgetsForFrame(context, frameId)
        set(value) {
            FramePrefs.setWidgetsForFrame(context, frameId, value)
        }

    var rowCount: Int
        get() = FramePrefs.getRowCountForFrame(context, frameId)
        set(value) {
            FramePrefs.setRowCountForFrame(context, frameId, value)
        }

    var colCount: Int
        get() = FramePrefs.getColCountForFrame(context, frameId)
        set(value) {
            FramePrefs.setColCountForFrame(context, frameId, value)
        }

    val gridSize: Pair<Int, Int>
        get() = FramePrefs.getGridSizeForFrame(context, frameId)

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

    private fun getInt(baseKey: String, def: Int): Int {
        return context.prefManager.getInt(keyFor(baseKey), def)
    }
    private fun putInt(baseKey: String, value: Int) {
        context.prefManager.putInt(keyFor(baseKey), value)
    }

    private fun getBoolean(baseKey: String, def: Boolean): Boolean {
        return context.prefManager.getBoolean(keyFor(baseKey), def)
    }
    private fun putBoolean(baseKey: String, value: Boolean) {
        context.prefManager.putBoolean(keyFor(baseKey), value)
    }

    fun keyFor(baseKey: String): String {
        return keyFor(frameId, baseKey)
    }

    companion object {
        fun keyFor(frameId: Int, baseKey: String): String {
            if (frameId == -1) {
                return baseKey
            }

            return "${baseKey}_${frameId}"
        }

        fun doAnyFramesHaveSettingEnabled(
            context: Context,
            baseKey: String,
            def: Boolean = false,
        ): Boolean {
            return (listOf(-1) + context.prefManager.currentSecondaryFrames).any { frameId ->
                context.prefManager.getBoolean(keyFor(frameId, baseKey), def)
            }
        }
    }
}

object FramePrefs {
    private const val KEY_FRAME_WIDGETS = "FRAME_WIDGETS_FOR_FRAME_"
    const val KEY_FRAME_ROW_COUNT = "FRAME_ROW_COUNT_FOR_FRAME_"
    const val KEY_FRAME_COL_COUNT = "FRAME_COL_COUNT_FOR_FRAME_"

    fun getWidgetsForFrame(context: Context, frameId: Int): Set<WidgetData> {
        if (frameId == -1) {
            return context.prefManager.currentWidgets
        }

        val stringVal = context.prefManager.getString(
            generatePrefKey(KEY_FRAME_WIDGETS, frameId),
            null,
        )

        return context.prefManager.gson.fromJson(
            stringVal,
            object : TypeToken<LinkedHashSet<WidgetData>>() {}.type,
        ) ?: LinkedHashSet()
    }

    fun setWidgetsForFrame(context: Context, frameId: Int, widgets: Collection<WidgetData>) {
        val set = LinkedHashSet(widgets.toSet())

        if (frameId == -1) {
            context.prefManager.currentWidgets = set
            return
        }

        context.prefManager.putString(
            generatePrefKey(KEY_FRAME_WIDGETS, frameId),
            context.prefManager.gson.toJson(set),
        )
    }

    fun getGridSizeForFrame(context: Context, frameId: Int): Pair<Int, Int> {
        return getRowCountForFrame(context, frameId) to getColCountForFrame(context, frameId)
    }

    fun getRowCountForFrame(context: Context, frameId: Int): Int {
        return if (frameId == -1) {
            context.prefManager.frameRowCount
        } else {
            context.prefManager.getInt(generatePrefKey(KEY_FRAME_ROW_COUNT, frameId), 1)
        }
    }

    fun getColCountForFrame(context: Context, frameId: Int): Int {
        return if (frameId == -1) {
            context.prefManager.frameColCount
        } else {
            context.prefManager.getInt(generatePrefKey(KEY_FRAME_COL_COUNT, frameId), 1)
        }
    }

    fun setRowCountForFrame(context: Context, frameId: Int, rowCount: Int) {
        if (frameId == -1) {
            context.prefManager.frameRowCount = rowCount
            return
        }

        context.prefManager.putInt(generatePrefKey(KEY_FRAME_ROW_COUNT, frameId), rowCount)
    }

    fun setColCountForFrame(context: Context, frameId: Int, colCount: Int) {
        if (frameId == -1) {
            context.prefManager.frameColCount = colCount
            return
        }

        context.prefManager.putInt(generatePrefKey(KEY_FRAME_COL_COUNT, frameId), colCount)
    }

    fun removeFrame(context: Context, frameId: Int) {
        if (frameId == -1) {
            return
        }

        context.prefManager.currentSecondaryFrames = context.prefManager.currentSecondaryFrames.toMutableList().apply {
            remove(frameId)
        }

        getWidgetsForFrame(context, frameId).forEach { data ->
            context.widgetHostCompat.deleteAppWidgetId(data.id)
        }
        context.prefManager.remove(generatePrefKey(KEY_FRAME_WIDGETS, frameId))
        context.prefManager.remove(generatePrefKey(KEY_FRAME_ROW_COUNT, frameId))
        context.prefManager.remove(generatePrefKey(KEY_FRAME_COL_COUNT, frameId))

        listOf(
            FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
            FrameSizeAndPosition.FrameType.SecondaryLockscreen.Landscape(frameId),
        ).forEach { type ->
            context.frameSizeAndPosition.removeSizeForType(type)
            context.frameSizeAndPosition.removePositionForType(type)
        }
    }

    fun generateCurrentWidgetsKey(id: Int): String {
        if (id == -1) {
            return PrefManager.KEY_CURRENT_WIDGETS
        }

        return "${KEY_FRAME_WIDGETS}_${id}"
    }

    fun generatePrefKey(baseKey: String, id: Int): String {
        if (id == -1) {
            when (baseKey) {
                KEY_FRAME_COL_COUNT -> PrefManager.KEY_FRAME_COL_COUNT
                KEY_FRAME_ROW_COUNT -> PrefManager.KEY_FRAME_ROW_COUNT
            }
        }

        return "${baseKey}_${id}"
    }
}
