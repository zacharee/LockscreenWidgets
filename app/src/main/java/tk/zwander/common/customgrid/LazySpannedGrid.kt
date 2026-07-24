package tk.zwander.common.customgrid

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

    val measurePolicy =
        remember(mainAxisCount, crossAxisCount, contentPadding, layoutDirection, state) {
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
                )
            }
        }

    LazyLayout(
        itemProvider = itemProviderLambda,
        modifier =
            modifier.scrollable(
                state = state,
                orientation = Orientation.Vertical,
                enabled = userScrollEnabled,
                reverseDirection = false,
                flingBehavior = flingBehavior,
            ),
        prefetchState = prefetchState,
        measurePolicy = measurePolicy,
    )
}
