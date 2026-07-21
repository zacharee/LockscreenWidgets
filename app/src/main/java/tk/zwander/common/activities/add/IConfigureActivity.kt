package tk.zwander.common.activities.add

import android.content.Context
import androidx.compose.ui.unit.IntSize
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.LSDisplay
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences

sealed interface IConfigureActivity {
    val holderId: Int
    val context: Context
    val display: LSDisplay

    val colCount: Int
    val rowCount: Int
    val width: Float
    val height: Float

    val gridSize: IntSize
        get() = IntSize(colCount, rowCount)

    var currentWidgets: Set<WidgetData>
}

interface IFrameConfigureActivity : IConfigureActivity {
    val framePrefs: FrameSpecificPreferences
        get() = FrameSpecificPreferences(frameId = holderId, context = context)

    override val colCount: Int
        get() = framePrefs.colCount
    override val rowCount: Int
        get() = framePrefs.rowCount

    override val height: Float
        get() = context.frameSizeAndPosition.getSizeForType(
            FrameSizeAndPosition.FrameType.LockNormal.Portrait,
            display,
        ).y
    override val width: Float
        get() = context.frameSizeAndPosition.getSizeForType(
            FrameSizeAndPosition.FrameType.LockNormal.Portrait,
            display,
        ).y

    override var currentWidgets: Set<WidgetData>
        get() = framePrefs.currentWidgets.toMutableSet()
        set(value) {
            framePrefs.currentWidgets = value
        }
}

interface IDrawerConfigureActivity : IConfigureActivity {
    override val colCount: Int
        get() = context.prefManager.drawerColCount
    override val rowCount: Int
        get() = 1

    override val width: Float
        get() = display.pxToDp(display.rotatedRealSize.x)
    override val height: Float
        get() = display.pxToDp(display.rotatedRealSize.y)

    override val gridSize: IntSize
        get() = IntSize(
            colCount,
            display.rotatedRealSize.y / context.resources.getDimensionPixelSize(R.dimen.drawer_row_height) - 10,
        )

    override var currentWidgets: Set<WidgetData>
        get() = context.prefManager.drawerWidgets
        set(value) {
            context.prefManager.drawerWidgets = LinkedHashSet(value)
        }
}