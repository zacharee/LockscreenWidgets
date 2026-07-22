package tk.zwander.common.customgrid

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Information about a single item placed during the last measure pass. */
data class LazySpannedGridItemInfo(
    val index: Int,
    val key: Any,
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val columnSpan: Int,
    val offset: IntOffset,
    val size: IntSize,
)

/**
 * A snapshot of the state of a [LazyVerticalSpannedGrid] or [LazyHorizontalSpannedGrid] after its
 * last measure pass.
 */
data class LazySpannedGridLayoutInfo(
    val visibleItemsInfo: List<LazySpannedGridItemInfo>,
    /** Total number of lines along the main/scrollable axis (rows for vertical, columns for horizontal). */
    val totalLineCount: Int,
    val viewportSize: IntSize,
    val cellSize: IntSize,
    val orientation: Orientation,
) {
    companion object {
        val Empty =
            LazySpannedGridLayoutInfo(
                visibleItemsInfo = emptyList(),
                totalLineCount = 0,
                viewportSize = IntSize.Zero,
                cellSize = IntSize.Zero,
                orientation = Orientation.Vertical,
            )
    }
}
