package tk.zwander.lockscreenwidgets.util

import android.content.Context

class SecondaryWidgetFrameDelegate(
    context: Context,
    id: Int,
    displayId: String,
) : WidgetFrameDelegate(
    context = context,
    id = id,
    initialDisplayId = displayId,
)
