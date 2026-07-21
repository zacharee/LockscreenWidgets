package tk.zwander.common.data.provider

import android.content.Context
import androidx.compose.ui.unit.IntSize
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.LSDisplay
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences

interface IBaseProvider {
    val holderId: Int
    val context: Context
    val display: LSDisplay?
}

interface IRowColumProvider : IBaseProvider {
    val colCount: Int
    val rowCount: Int

    val gridSize: IntSize
        get() = IntSize(colCount, rowCount)
}

interface ICurrentWidgetsProvider : IBaseProvider {
    var currentWidgets: Set<WidgetData>
}

interface IWidthHeightProvider : IBaseProvider {
    val width: Float
    val height: Float
}

interface IFramePrefsProvider : IBaseProvider {
    val framePrefs: FrameSpecificPreferences
        get() = FrameSpecificPreferences(frameId = holderId, context = context)
}

interface IFrameProvider : IRowColumProvider, ICurrentWidgetsProvider, IWidthHeightProvider, IFramePrefsProvider {
    override val colCount: Int
        get() = framePrefs.colCount
    override val rowCount: Int
        get() = framePrefs.rowCount

    override val height: Float
        get() = display?.let {
            context.frameSizeAndPosition.getSizeForType(
                FrameSizeAndPosition.FrameType.LockNormal.Portrait,
                it,
            )
        }?.y ?: 0f
    override val width: Float
        get() = display?.let {
            context.frameSizeAndPosition.getSizeForType(
                FrameSizeAndPosition.FrameType.LockNormal.Portrait,
                it,
            )
        }?.x ?: 0f

    override var currentWidgets: Set<WidgetData>
        get() = framePrefs.currentWidgets
        set(value) {
            framePrefs.currentWidgets = value
        }
}

interface IDrawerProvider : IRowColumProvider, ICurrentWidgetsProvider, IWidthHeightProvider {
    override val colCount: Int
        get() = context.prefManager.drawerColCount
    override val rowCount: Int
        get() = ((display?.rotatedRealSize?.y ?: 0) / context.resources.getDimensionPixelSize(R.dimen.drawer_row_height) - 10).coerceAtLeast(1)

    override val width: Float
        get() = display?.pxToDp(display!!.rotatedRealSize.x) ?: 0f
    override val height: Float
        get() = display?.pxToDp(display!!.rotatedRealSize.y) ?: 0f

    override val gridSize: IntSize
        get() = IntSize(
            colCount,
            rowCount,
        )

    override var currentWidgets: Set<WidgetData>
        get() = context.prefManager.drawerWidgets
        set(value) {
            context.prefManager.drawerWidgets = LinkedHashSet(value)
        }
}
