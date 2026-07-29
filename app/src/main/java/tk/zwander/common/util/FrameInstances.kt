package tk.zwander.common.util

import android.content.Context
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate
import tk.zwander.lockscreenwidgets.util.SecondaryWidgetFrameDelegate

object FrameInstances {
    val secondaryFrameDelegates = hashMapOf<Int, SecondaryWidgetFrameDelegate>()

    fun allInstances(context: Context): Map<Int, MainWidgetFrameDelegate?> {
        return [MainWidgetFrameDelegate.ID to MainWidgetFrameDelegate.peekInstance(context)].toMap() + secondaryFrameDelegates
    }
}
