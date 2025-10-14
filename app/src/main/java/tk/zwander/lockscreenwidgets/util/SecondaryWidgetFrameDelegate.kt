package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.view.WindowManager

class SecondaryWidgetFrameDelegate(
    context: Context,
    id: Int,
    wm: WindowManager,
    displayId: Int,
) : MainWidgetFrameDelegate(context, id, wm, displayId)
