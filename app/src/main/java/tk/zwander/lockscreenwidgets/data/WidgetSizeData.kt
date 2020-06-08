package tk.zwander.lockscreenwidgets.data

import kotlin.math.max

data class WidgetSizeData(
    var widgetId: Int,
    private var widgetWidthSpan: Int,
    private var widgetHeightSpan: Int
) {
    var safeWidgetWidthSpan: Int
        get() = max(widgetWidthSpan, 1)
        set(value) {
            widgetWidthSpan = max(value, 1)
        }

    var safeWidgetHeightSpan: Int
        get() = max(widgetHeightSpan, 1)
        set(value) {
            widgetHeightSpan = max(value, 1)
        }
}