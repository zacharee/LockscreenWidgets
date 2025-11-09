package tk.zwander.lockscreenwidgets.util

import android.content.Context
import tk.zwander.common.util.requireLsDisplayManager

class SecondaryWidgetFrameDelegate(
    context: Context,
    id: Int,
    displayId: Int,
) : MainWidgetFrameDelegate(
    context = context.createDisplayContext(
        context.requireLsDisplayManager.requireDisplay(displayId).display,
    ),
    id = id,
    displayId = displayId,
)
