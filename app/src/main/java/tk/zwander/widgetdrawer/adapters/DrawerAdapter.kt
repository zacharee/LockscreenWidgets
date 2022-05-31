package tk.zwander.widgetdrawer.adapters

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.Event
import tk.zwander.lockscreenwidgets.util.dpAsPx
import tk.zwander.lockscreenwidgets.util.eventManager
import tk.zwander.lockscreenwidgets.util.prefManager
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
        get() = Int.MAX_VALUE
    override var currentWidgets: MutableCollection<WidgetData>
        get() = host.context.prefManager.drawerWidgets
        set(value) {
            host.context.prefManager.drawerWidgets = LinkedHashSet(value)
        }

    override fun launchAddActivity() {
        host.context.eventManager.sendEvent(Event.CloseDrawer)
        host.context.eventManager.sendEvent(Event.LaunchAddDrawerWidget)
    }

    override fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo) {
        ReconfigureDrawerWidgetActivity.launch(host.context, id, providerInfo)
    }

    override fun View.onWidgetResize(data: WidgetData, params: ViewGroup.LayoutParams) {
        params.height = (data.size?.safeWidgetHeightSpan ?: 1) * context.dpAsPx(50f)
    }
}