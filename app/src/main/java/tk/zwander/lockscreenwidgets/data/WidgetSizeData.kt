package tk.zwander.lockscreenwidgets.data

import kotlin.math.max

/**
 * Keep track of the size of each widget.
 */
data class WidgetSizeData(
    var widgetId: Int,
    private var widgetWidthSpan: Int,
    private var widgetHeightSpan: Int
) {
    /**
     * Don't allow the user to have a zero-width widget.
     */
    var safeWidgetWidthSpan: Int
        get() = max(widgetWidthSpan, 1)
        set(value) {
            widgetWidthSpan = max(value, 1)
        }

    /**
     * Don't allow the user to have a zero-height widget.
     */
    var safeWidgetHeightSpan: Int
        get() = max(widgetHeightSpan, 1)
        set(value) {
            widgetHeightSpan = max(value, 1)
        }
}