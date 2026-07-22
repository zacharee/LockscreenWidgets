package tk.zwander.common.customgrid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasurePolicy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

    val itemProviderLambda = rememberLazySpannedGridItemProviderLambda(state, content)
    val layoutDirection = LocalLayoutDirection.current
    val placementCache = remember { SpannedGridPlacementCache() }
    @OptIn(ExperimentalFoundationApi::class)
    val prefetchState = remember { LazyLayoutPrefetchState() }
    @OptIn(ExperimentalFoundationApi::class)
    state.prefetchState = prefetchState

    val measurePolicy =
        remember(rowCount, columnCount, contentPadding, layoutDirection, state) {
            LazyLayoutMeasurePolicy { constraints ->
                measureSpannedGrid(
                    measureScope = this,
                    itemProvider = itemProviderLambda(),
                    state = state,
                    orientation = Orientation.Horizontal,
                    crossAxisCount = rowCount,
                    mainAxisLineCount = columnCount,
                    contentPadding = contentPadding,
                    layoutDirection = layoutDirection,
                    constraints = constraints,
                    placementCache = placementCache,
                )
            }
        }

    LazyLayout(
        itemProvider = itemProviderLambda,
        modifier =
            modifier.scrollable(
                state = state,
                orientation = Orientation.Horizontal,
                enabled = userScrollEnabled,
                // Items are placed with placeRelative, which already mirrors x for RTL. A
                // physical drag in a given screen direction therefore corresponds to the
                // opposite logical (index) direction in RTL vs. LTR, so the delta needs an
                // extra flip here to keep "content follows finger" scrolling natural in RTL.
                reverseDirection = layoutDirection == LayoutDirection.Rtl,
                flingBehavior = flingBehavior,
            ),
        prefetchState = prefetchState,
        measurePolicy = measurePolicy,
    )
}
