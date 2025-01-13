package tk.zwander.lockscreenwidgets.util

import android.content.Context
import com.google.gson.reflect.TypeToken
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.prefManager

object FramePrefs {
    private const val KEY_FRAME_WIDGETS = "FRAME_WIDGETS_FOR_FRAME_"
    private const val KEY_FRAME_ROW_COUNT = "FRAME_ROW_COUNT_FOR_FRAME_"
    private const val KEY_FRAME_COL_COUNT = "FRAME_COL_COUNT_FOR_FRAME_"

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
        }

        context.prefManager.putString(
            generatePrefKey(KEY_FRAME_WIDGETS, frameId),
            context.prefManager.gson.toJson(set),
        )
    }

    fun getSizeForFrame(context: Context, frameId: Int): Pair<Int, Int> {
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

    fun setSizeForFrame(context: Context, frameId: Int, size: Pair<Int, Int>) {
        if (frameId == -1) {
            context.prefManager.frameRowCount = size.first
            context.prefManager.frameColCount = size.second
        }

        context.prefManager.putInt(generatePrefKey(KEY_FRAME_ROW_COUNT, frameId), size.first)
        context.prefManager.putInt(generatePrefKey(KEY_FRAME_COL_COUNT, frameId), size.second)
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
    }

    private fun generatePrefKey(baseKey: String, id: Int): String {
        return "${baseKey}_${id}"
    }
}
