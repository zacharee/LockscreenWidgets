package tk.zwander.common.customgrid

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A lazily-composed vertical grid where items can span multiple rows as well as multiple columns,
 * analogous to `SpannedGridLayoutManager`. Unlike [androidx.compose.foundation.lazy.grid.LazyVerticalGrid],
 * cells are a fixed, uniform size derived from [columnCount]/[rowCount] and the layout's own
 * measured bounds (matching how the app's existing RecyclerView-based widget frame sizes cells:
 * `frameExtent / referenceSpanCount`) rather than being measured from item content.
 *
 * See [LazyHorizontalSpannedGrid] for the horizontal counterpart — both share the same placement
 * algorithm and measure logic (see [measureSpannedGrid]).
 *
 * @param columnCount fixed number of columns (the cross-axis span count).
 * @param rowCount reference/minimum number of rows used to compute a fixed cell height. If the
 *   packed items need more rows than this, the grid simply becomes scrollable past it.
 */
@Composable
fun LazyVerticalSpannedGrid(
    columnCount: Int,
    rowCount: Int,
    modifier: Modifier = Modifier,
    state: LazySpannedGridState = rememberLazySpannedGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    userScrollEnabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    content: LazySpannedGridScope.() -> Unit,
) {
    require(columnCount > 0) { "columnCount must be positive, was $columnCount" }
    require(rowCount > 0) { "rowCount must be positive, was $rowCount" }

    LazySpannedGrid(
        mainAxisCount = rowCount,
        crossAxisCount = columnCount,
        orientation = Orientation.Vertical,
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        flingBehavior = flingBehavior,
        content = content,
    )
}
