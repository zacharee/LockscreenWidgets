package tk.zwander.lockscreenwidgets.activities.add

import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.prefManager

class AddFrameWidgetActivity : AddWidgetActivity() {
    override var currentWidgets: MutableSet<WidgetData>
        get() = prefManager.currentWidgets
        set(value) {
            prefManager.currentWidgets = LinkedHashSet(value)
        }
}