package tk.zwander.common.util

import android.content.Context
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate

object FrameInstances {
    val secondaryFrameDelegates = hashMapOf<Int, WidgetFrameDelegate>()

    fun allInstances(context: Context): Map<Int, WidgetFrameDelegate?> {
        return [WidgetFrameDelegate.ID to WidgetFrameDelegate.peekInstance(context)].toMap() + secondaryFrameDelegates
    }
}
