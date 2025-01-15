package tk.zwander.lockscreenwidgets.util

import android.content.Context
import com.google.gson.reflect.TypeToken
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.prefManager

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
        return "${baseKey}_${id}"
    }
}
