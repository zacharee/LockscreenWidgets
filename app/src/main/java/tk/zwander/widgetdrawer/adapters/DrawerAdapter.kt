package tk.zwander.widgetdrawer.adapters

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import tk.zwander.common.activities.SelectIconPackActivity
import tk.zwander.common.adapters.BaseAdapter
import tk.zwander.common.data.WidgetData
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.widgetdrawer.activities.add.ReconfigureDrawerWidgetActivity
import tk.zwander.widgetdrawer.util.DrawerDelegate

class DrawerAdapter(
    context: Context,
    rootView: View,
    displayId: String,
    viewModel: DrawerDelegate.DrawerViewModel,
    onRemoveCallback: (WidgetData, Int) -> Unit,
) : BaseAdapter(
    -2,
    context,
    rootView,
    onRemoveCallback,
    displayId,
    viewModel,
) {
    override val colCount: Int
        get() = context.prefManager.drawerColCount
    override val rowCount: Int
        get() = (lsDisplay.rotatedRealSize.y / context.resources.getDimensionPixelSize(R.dimen.drawer_row_height)) - 5
    override val minRowSpan: Int
        get() = 5
    override val rowSpanForAddButton: Int
        get() = 20
    override var currentWidgets: Collection<WidgetData>
        get() = context.prefManager.drawerWidgets
        set(value) {
            context.prefManager.drawerWidgets = LinkedHashSet(value)
        }

    override fun launchAddActivity() {
        context.eventManager.sendEvent(Event.CloseDrawer)
        context.eventManager.sendEvent(Event.LaunchAddDrawerWidget(true))
    }

    override fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo) {
        context.eventManager.sendEvent(Event.CloseDrawer)
        ReconfigureDrawerWidgetActivity.launch(context, id, providerInfo)
    }

    override fun View.onWidgetResize(data: WidgetData, params: ViewGroup.LayoutParams, amount: Int, direction: Int) {
        params.height += (amount * direction)
    }

    override fun launchShortcutIconOverride(id: Int) {
        context.eventManager.sendEvent(Event.CloseDrawer)
        SelectIconPackActivity.launchForOverride(context, id, true)
    }

    override fun getThresholdPx(which: WidgetResizeListener.Which): Int {
        return context.run {
            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                this@DrawerAdapter.lsDisplay.rotatedRealSize.x / colCount
            } else {
                resources.getDimensionPixelSize(R.dimen.drawer_row_height)
            }
        }
    }
}