package tk.zwander.lockscreenwidgets.adapters

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import tk.zwander.common.activities.SelectIconPackActivity
import tk.zwander.common.adapters.BaseAdapter
import tk.zwander.common.data.WidgetData
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.Event
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureFrameWidgetActivity
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

/**
 * The adapter for the widget frame itself.
 */
open class WidgetFrameAdapter(
    frameId: Int,
    context: Context,
    rootView: View,
    displayId: Int,
    onRemoveCallback: (WidgetData, Int) -> Unit,
    viewModel: MainWidgetFrameDelegate.WidgetFrameViewModel,
    private val saveTypeGetter: () -> FrameSizeAndPosition.FrameType,
) : BaseAdapter(frameId, context, rootView, onRemoveCallback, displayId, viewModel) {
    override val colCount: Int
        get() = FramePrefs.getColCountForFrame(context, holderId)
    override val rowCount: Int
        get() = FramePrefs.getRowCountForFrame(context, holderId)
    override val minRowSpan: Int
        get() = 1
    override val rowSpanForAddButton: Int
        get() = rowCount
    override var currentWidgets: Collection<WidgetData>
        get() = FramePrefs.getWidgetsForFrame(context, holderId)
        set(value) {
            FramePrefs.setWidgetsForFrame(context, holderId, value)
        }

    override fun launchAddActivity() {
        context.eventManager.sendEvent(Event.LaunchAddWidget(holderId))
    }

    override fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo) {
        ReconfigureFrameWidgetActivity.launch(context, id, holderId, providerInfo)
    }

    override fun View.onWidgetResize(
        data: WidgetData,
        params: ViewGroup.LayoutParams,
        amount: Int,
        direction: Int
    ) {
        params.width =
            params.width / colCount * (data.size?.safeWidgetWidthSpan ?: 1)
        params.height =
            params.height / rowCount * (data.size?.safeWidgetHeightSpan ?: 1)
    }

    override fun launchShortcutIconOverride(id: Int) {
        SelectIconPackActivity.launchForOverride(context, id)
    }

    override fun getThresholdPx(which: WidgetResizeListener.Which): Int {
        return context.run {
            val frameSize = frameSizeAndPosition.getSizeForType(saveTypeGetter(), this@WidgetFrameAdapter.display)
            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                frameSize.x.toInt() / colCount
            } else {
                frameSize.y.toInt() / rowCount
            }
        }
    }
}