package tk.zwander.widgetdrawer.adapters

import android.appwidget.AppWidgetManager
import android.view.WindowManager
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.prefManager

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
}