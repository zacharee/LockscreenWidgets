package tk.zwander.lockscreenwidgets.util

import android.content.Context

class SecondaryWidgetFrameDelegate(
    context: Context,
    id: Int,
    displayId: String,
) : MainWidgetFrameDelegate(
    context = context,
    id = id,
    targetDisplayId = displayId,
)
