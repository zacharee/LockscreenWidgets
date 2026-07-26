package tk.zwander.common.customgrid

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Information about a single item placed during the last measure pass. */
data class LazySpannedGridItemInfo(
    /**
     * The index of the item in the full list.
     */
    val index: Int,
    /**
     * The key assigned to the item with the [key] lambda in the [items] or [item] function.
     * Will be [DefaultLazyKey] if no key was provided.
     */
    val key: Any,
    /**
     * Which row this item is in, in relation to all items.
     */
    val row: Int,
    /**
     * Which column this item is in, in relation to all items.
     */
    val column: Int,
    /**
     * The row span specified for this item.
     */
    val rowSpan: Int,
    /**
     * The column span specified for this item.
     */
    val columnSpan: Int,
    /**
     * The offset relative to the rest of the items, accounting for scroll.
     */
    val offset: IntOffset,
    /**
     * Measured size of item in pixels.
     */
    val size: IntSize,
)

/**
 * A snapshot of the state of a [LazyVerticalSpannedGrid] or [LazyHorizontalSpannedGrid] after its
 * last measure pass.
 */
data class LazySpannedGridLayoutInfo(
    /**
     * Information about currently visible items.
     */
    val visibleItemsInfo: List<LazySpannedGridItemInfo>,
    /** Total number of lines along the main/scrollable axis (rows for vertical, columns for horizontal). */
    val totalLineCount: Int,
    /**
     * The measured size of the containing grid in pixels.
     */
    val viewportSize: IntSize,
    /**
     * The measured size of an individual cell (1 row by 1 column) in pixels.
     */
    val cellSize: IntSize,
    /**
     * Scroll direction of the grid.
     */
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
