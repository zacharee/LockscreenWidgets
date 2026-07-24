package tk.zwander.common.customgrid

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A lazily-composed horizontal grid where items can span multiple rows as well as multiple
 * columns, analogous to `SpannedGridLayoutManager`'s horizontal orientation. This is the
 * horizontal counterpart of [LazyVerticalSpannedGrid] — both share the same placement algorithm
 * and measure logic (see [measureSpannedGrid]); only which axis is fixed vs. scrollable differs.
 *
 * @param rowCount fixed number of rows (the cross-axis span count).
 * @param columnCount reference/minimum number of columns used to compute a fixed cell width. If
 *   the packed items need more columns than this, the grid simply becomes scrollable past it.
 */
@Composable
fun LazyHorizontalSpannedGrid(
    rowCount: Int,
    columnCount: Int,
    modifier: Modifier = Modifier,
    state: LazySpannedGridState = rememberLazySpannedGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    userScrollEnabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    content: LazySpannedGridScope.() -> Unit,
) {
    require(rowCount > 0) { "rowCount must be positive, was $rowCount" }
    require(columnCount > 0) { "columnCount must be positive, was $columnCount" }

    LazySpannedGrid(
        mainAxisCount = columnCount,
        crossAxisCount = rowCount,
        orientation = Orientation.Horizontal,
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
        flingBehavior = flingBehavior,
        content = content,
    )
}
