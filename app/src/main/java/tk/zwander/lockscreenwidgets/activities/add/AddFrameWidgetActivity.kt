package tk.zwander.lockscreenwidgets.activities.add

import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.lockscreenwidgets.util.FramePrefs

class AddFrameWidgetActivity : AddWidgetActivity() {
    override var currentWidgets: MutableSet<WidgetData>
        get() = FramePrefs.getWidgetsForFrame(this, holderId).toMutableSet()
        set(value) {
            FramePrefs.setWidgetsForFrame(this, holderId, value)
        }
}