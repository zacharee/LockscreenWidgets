@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package tk.zwander.common.customgrid

import androidx.compose.foundation.lazy.layout.LazyLayoutItemAnimation
import androidx.compose.foundation.lazy.layout.LazyLayoutItemAnimator
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasuredItem
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastForEachIndexed

/**
 * A single measured [LazySpannedGridScope.item]/[LazySpannedGridScope.items] entry, in the shape
 * [LazyLayoutItemAnimator] needs to drive its `animateItem()` fade-in/placement/fade-out
 * animations — mirrors `androidx.compose.foundation.lazy.grid.LazyGridMeasuredItem`.
 *
 * Unlike the grid version, [position] doesn't need to do any RTL cross-axis mirroring itself:
 * [measureSpannedGrid] already computes a *logical* offset (mirrored by [place] via
 * `placeRelative*`), the same way the rest of this grid's placement always has.
 */
internal class SpannedGridMeasuredItem(
    override val index: Int,
    override val key: Any,
    override val placeables: List<Placeable>,
    override val constraints: Constraints,
    override val lane: Int,
    override val span: Int,
    private val animator: LazyLayoutItemAnimator<SpannedGridMeasuredItem>,
) : LazyLayoutMeasuredItem {
    override val horizontalAxisSize: Int = constraints.minWidth
    override val verticalAxisSize: Int = constraints.minHeight
    override val horizontalAxisSpacing: Int = 0
    override val verticalAxisSpacing: Int = 0

    private var offset: IntOffset = IntOffset.Zero

    /** Set by the animator when it re-measures this item on its way out; unused by our own measure pass. */
    var nonScrollableItem: Boolean = false
        private set

    /**
     * Set by [measureSpannedGrid] for the item currently being drag-reordered — see
     * [LazySpannedGridState.suppressPlacementAnimationKey]. When true, [place] ignores any active
     * placement delta and renders directly at [offset], while still honoring an active fade
     * [LazyLayoutItemAnimation.layer] if one is set.
     */
    var skipPlacementAnimation: Boolean = false

    override fun getOffset(placeableIndex: Int): IntOffset = offset

    override fun position(
        horizontalAxisOffset: Int,
        verticalAxisOffset: Int,
        layoutWidth: Int,
        layoutHeight: Int,
    ) {
        offset = IntOffset(horizontalAxisOffset, verticalAxisOffset)
    }

    override fun makeNonScrollable() {
        nonScrollableItem = true
    }

    /** Places every placeable, applying this item's active [LazyLayoutItemAnimation] (if any) on top of its target [offset]. */
    fun place(scope: Placeable.PlacementScope, isLookingAhead: Boolean) =
        with(scope) {
            placeables.fastForEachIndexed { placeableIndex, placeable ->
                val animation = animator.getAnimation(key, placeableIndex)
                var renderOffset = offset
                val layer: GraphicsLayer?
                if (animation != null) {
                    if (isLookingAhead) {
                        animation.lookaheadOffset = offset
                    } else if (skipPlacementAnimation) {
                        // Stop the spring in the background too, not just visually here — so it
                        // doesn't have a stale non-zero delta left to suddenly resume applying if
                        // this key stops being suppressed before the animation would've settled.
                        animation.cancelPlacementAnimation()
                    } else {
                        val targetOffset =
                            if (animation.lookaheadOffset != LazyLayoutItemAnimation.NotInitialized) {
                                animation.lookaheadOffset
                            } else {
                                offset
                            }
                        renderOffset = targetOffset + animation.placementDelta
                    }
                    layer = animation.layer
                } else {
                    layer = null
                }
                if (!isLookingAhead) {
                    animation?.placementOffset = renderOffset
                }
                if (layer != null) {
                    placeable.placeRelativeWithLayer(renderOffset, layer)
                } else {
                    placeable.placeRelativeWithLayer(renderOffset)
                }
            }
        }
}
