package tk.zwander.common.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.max

/**
 * Keep track of the size of each widget.
 */
@Parcelize
data class WidgetSizeData(
    private val widgetWidthSpan: Int,
    private val widgetHeightSpan: Int
) : Parcelable {
    /**
     * Don't allow the user to have a zero-width widget.
     */
    val safeWidgetWidthSpan: Int
        get() = max(widgetWidthSpan, 1)

    /**
     * Don't allow the user to have a zero-height widget.
     */
    val safeWidgetHeightSpan: Int
        get() = max(widgetHeightSpan, 1)

    fun safeCopy(
        widgetWidthSpan: Int = safeWidgetWidthSpan,
        widgetHeightSpan: Int = safeWidgetHeightSpan,
    ): WidgetSizeData {
        return WidgetSizeData(
            widgetWidthSpan = max(widgetWidthSpan, 1),
            widgetHeightSpan = max(widgetHeightSpan, 1),
        )
    }
}