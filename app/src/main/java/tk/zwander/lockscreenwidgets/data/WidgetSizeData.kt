package tk.zwander.lockscreenwidgets.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.max

/**
 * Keep track of the size of each widget.
 */
@Parcelize
data class WidgetSizeData(
    private var widgetWidthSpan: Int,
    private var widgetHeightSpan: Int
) : Parcelable {
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