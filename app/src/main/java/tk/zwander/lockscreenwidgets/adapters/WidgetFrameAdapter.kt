package tk.zwander.lockscreenwidgets.adapters

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import tk.zwander.common.adapters.BaseAdapter
import tk.zwander.common.data.WidgetData
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.Event
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureFrameWidgetActivity

/**
 * The adapter for the widget frame itself.
 */
open class WidgetFrameAdapter(
    context: Context,
    onRemoveCallback: (WidgetData, Int) -> Unit,
    private val saveTypeGetter: () -> FrameSizeAndPosition.FrameType,
) : BaseAdapter(context, onRemoveCallback) {
    override val colCount: Int
        get() = context.prefManager.frameColCount
    override val rowCount: Int
        get() = context.prefManager.frameRowCount
    override val minColSpan: Int
        get() = 1
    override val minRowSpan: Int
        get() = 1
    override val rowSpanForAddButton: Int
        get() = rowCount
    override var currentWidgets: Collection<WidgetData>
        get() = context.prefManager.currentWidgets
        set(value) {
            context.prefManager.currentWidgets = LinkedHashSet(value)
        }
    override val widgetCornerRadius: Float
        get() = context.prefManager.frameWidgetCornerRadiusDp

    override fun launchAddActivity() {
        context.eventManager.sendEvent(Event.LaunchAddWidget)
    }

    override fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo) {
        ReconfigureFrameWidgetActivity.launch(context, id, providerInfo)
    }

    override fun View.onWidgetResize(
        data: WidgetData,
        params: ViewGroup.LayoutParams,
        amount: Int,
        direction: Int
    ) {
        params.width =
            params.width / context.prefManager.frameColCount * (data.size?.safeWidgetWidthSpan ?: 1)
        params.height =
            params.height / context.prefManager.frameRowCount * (data.size?.safeWidgetHeightSpan ?: 1)
    }

    override fun getThresholdPx(which: WidgetResizeListener.Which): Int {
        return context.run {
            val frameSize = frameSizeAndPosition.getSizeForType(saveTypeGetter())
            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                frameSize.x.toInt() / prefManager.frameColCount
            } else {
                frameSize.y.toInt() / prefManager.frameRowCount
            }
        }
    }
}