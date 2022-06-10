package tk.zwander.widgetdrawer.adapters

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.listeners.WidgetResizeListener
import tk.zwander.lockscreenwidgets.util.Event
import tk.zwander.lockscreenwidgets.util.eventManager
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.util.screenSize
import tk.zwander.widgetdrawer.activities.add.ReconfigureDrawerWidgetActivity

class DrawerAdapter(
    manager: AppWidgetManager,
    host: WidgetHostCompat,
    params: WindowManager.LayoutParams,
    onRemoveCallback: (WidgetFrameAdapter, WidgetData, Int) -> Unit
) : WidgetFrameAdapter(manager, host, params, onRemoveCallback) {
    override val colCount: Int
        get() = host.context.prefManager.drawerColCount
    override val rowCount: Int
        get() = (host.context.screenSize.y / host.context.resources.getDimensionPixelSize(R.dimen.drawer_row_height)) - 5
    override val minRowSpan: Int
        get() = 10
    override var currentWidgets: MutableCollection<WidgetData>
        get() = host.context.prefManager.drawerWidgets
        set(value) {
            host.context.prefManager.drawerWidgets = LinkedHashSet(value)
        }
    override val widgetCornerRadius: Float
        get() = host.context.prefManager.drawerWidgetCornerRadiusDp

    override fun launchAddActivity() {
        host.context.eventManager.sendEvent(Event.CloseDrawer)
        host.context.eventManager.sendEvent(Event.LaunchAddDrawerWidget(true))
    }

    override fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo) {
        host.context.eventManager.sendEvent(Event.CloseDrawer)
        ReconfigureDrawerWidgetActivity.launch(host.context, id, providerInfo)
    }

    override fun View.onWidgetResize(data: WidgetData, params: ViewGroup.LayoutParams, amount: Int, direction: Int) {
        params.height = params.height + (amount * direction)
    }

    override fun getThresholdPx(which: WidgetResizeListener.Which): Int {
        return host.context.run {
            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                screenSize.x / prefManager.drawerColCount
            } else {
                resources.getDimensionPixelSize(R.dimen.drawer_row_height)
            }
        }
    }
}