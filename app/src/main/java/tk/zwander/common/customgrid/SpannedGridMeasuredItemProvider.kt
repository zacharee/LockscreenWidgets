@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package tk.zwander.common.customgrid

import androidx.compose.foundation.lazy.layout.LazyLayoutItemAnimator
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasuredItemProvider
import androidx.compose.ui.unit.Constraints

/**
 * Abstracts away the subcomposition from [measureSpannedGrid] — mirrors
 * `androidx.compose.foundation.lazy.grid.LazyGridMeasuredItemProvider`. Created fresh for every
 * measure pass since it's a thin wrapper (see [LazyLayoutMeasuredItemProvider]'s own per-index
 * placeable cache for why this doesn't need caching across passes).
 */
internal class SpannedGridMeasuredItemProvider(
    private val itemProvider: LazySpannedGridItemProvider,
    private val measureScope: LazyLayoutMeasureScope,
    private val animator: LazyLayoutItemAnimator<SpannedGridMeasuredItem>,
) : LazyLayoutMeasuredItemProvider<SpannedGridMeasuredItem>() {
    override fun getAndMeasure(
        index: Int,
        lane: Int,
        span: Int,
        constraints: Constraints,
    ): SpannedGridMeasuredItem {
        val key = itemProvider.getKey(index)
        val placeables = measureScope.getPlaceables(index, constraints)
        return SpannedGridMeasuredItem(
            index = index,
            key = key,
            placeables = placeables,
            constraints = constraints,
            lane = lane,
            span = span,
            animator = animator,
        )
    }
}
