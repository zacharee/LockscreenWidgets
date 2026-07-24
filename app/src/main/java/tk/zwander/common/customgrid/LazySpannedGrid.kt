@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package tk.zwander.common.customgrid

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.scrollableArea
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasurePolicy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.lazyLayoutItemAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun LazySpannedGrid(
    mainAxisCount: Int,
    crossAxisCount: Int,
    orientation: Orientation,
    modifier: Modifier = Modifier,
    state: LazySpannedGridState = rememberLazySpannedGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    userScrollEnabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    content: LazySpannedGridScope.() -> Unit,
) {
    val itemProviderLambda = rememberLazySpannedGridItemProviderLambda(state, content)
    val layoutDirection = LocalLayoutDirection.current
    val placementCache = remember { SpannedGridPlacementCache() }
    val prefetchState = remember { LazyLayoutPrefetchState() }
    state.prefetchState = prefetchState
    val graphicsContext = LocalGraphicsContext.current

    val measurePolicy =
        remember(mainAxisCount, crossAxisCount, contentPadding, layoutDirection, state, graphicsContext) {
            LazyLayoutMeasurePolicy { constraints ->
                measureSpannedGrid(
                    measureScope = this,
                    itemProvider = itemProviderLambda(),
                    state = state,
                    orientation = orientation,
                    crossAxisCount = crossAxisCount,
                    mainAxisLineCount = mainAxisCount,
                    contentPadding = contentPadding,
                    layoutDirection = layoutDirection,
                    constraints = constraints,
                    placementCache = placementCache,
                    graphicsContext = graphicsContext,
                )
            }
        }

    LazyLayout(
        itemProvider = itemProviderLambda,
        modifier =
            modifier
                .lazyLayoutItemAnimator(state.itemAnimator)
                .scrollableArea(
                    state = state,
                    orientation = orientation,
                    enabled = userScrollEnabled,
                    reverseScrolling = false,
                    flingBehavior = flingBehavior,
                ),
        prefetchState = prefetchState,
        measurePolicy = measurePolicy,
    )
}
