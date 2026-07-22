package tk.zwander.common.customgrid

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.sign

/** Fling velocities below this (in px/s) are treated as "stopping", not "continuing a direction". */
private val MinFlingVelocity = 400.dp

/**
 * A [SnapLayoutInfoProvider] that snaps [gridState] to the start of the nearest *page* — a run of
 * [LazySpannedGridState.mainAxisLineCount] main-axis lines, i.e. one full reference rowCount (for
 * [LazyVerticalSpannedGrid]) or columnCount (for [LazyHorizontalSpannedGrid]) worth of lines, which
 * is exactly one viewport's worth since that reference count is what [LazySpannedGridState.lineSizePx]
 * is derived from. Not per individual line — flinging pages through the grid a full screen at a time.
 *
 * Unlike the standard `SnapLayoutInfoProvider(LazyGridState, ...)`, this doesn't need to scan
 * [LazySpannedGridLayoutInfo.visibleItemsInfo] for boundaries: every line in this grid is the same
 * fixed size, so page boundaries are just multiples of `lineSizePx * mainAxisLineCount`, and the
 * one before/after the current scroll position can be computed directly.
 *
 * There's no generalized `SnapPosition` support (start/center/end) — unlike a `LazyGrid`, there's
 * no single "item" to position within a page, and aligning a page's start with the viewport start
 * is the natural paging behavior for a uniform-line grid.
 *
 * [density] is only used to convert [MinFlingVelocity] to pixels.
 */
fun SnapLayoutInfoProvider(
    gridState: LazySpannedGridState,
    density: Density,
): SnapLayoutInfoProvider =
    object : SnapLayoutInfoProvider {
        private val pageSizePx: Int
            get() = gridState.lineSizePx * gridState.mainAxisLineCount.coerceAtLeast(1)

        override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
            // Let the decay animation cover everything except the last page's worth of distance,
            // then snap precisely — mirrors LazyGridSnapLayoutInfoProvider's own approach, using
            // pageSizePx in place of an average item size (every page here is the same size).
            return (decayOffset.absoluteValue - pageSizePx).coerceAtLeast(0f) * decayOffset.sign
        }

        override fun calculateSnapOffset(velocity: Float): Float {
            val pageSizePx = pageSizePx
            if (pageSizePx <= 0) return 0f

            // LazySpannedGridState.internalScrollableState is deliberately inverted from the
            // naive scrollBy convention (see its own doc comment): a *positive* delta — and
            // therefore a *positive* fling velocity, measured in the same units — moves the
            // scroll offset *backward*, towards the start. So a positive velocity here means
            // "continue toward the start", not "toward the next item" the way most Compose snap
            // providers assume; these are named plainly (not "next"/"previous") so that inversion
            // can't be missed on a future read.
            val scrollOffsetPx = gridState.firstVisibleLine * gridState.lineSizePx + gridState.firstVisibleLineScrollOffset
            val r = (scrollOffsetPx % pageSizePx).toFloat() // how far into the current page we are
            val backwardDelta = r // scrollBy delta that aligns to the current page's start
            val forwardDelta = r - pageSizePx // scrollBy delta that aligns to the next page's start

            val minFlingVelocityPx = with(density) { MinFlingVelocity.toPx() }
            return when {
                velocity.absoluteValue < minFlingVelocityPx ->
                    if (backwardDelta.absoluteValue <= forwardDelta.absoluteValue) backwardDelta else forwardDelta
                velocity > 0 -> backwardDelta
                else -> forwardDelta
            }
        }
    }

/**
 * Remembers a [FlingBehavior] that snaps [gridState] to the start of the nearest page (see the
 * [SnapLayoutInfoProvider] overload for [LazySpannedGridState]) after a fling.
 */
@Composable
fun rememberSpannedGridSnapFlingBehavior(gridState: LazySpannedGridState): FlingBehavior {
    val density = LocalDensity.current
    val snapLayoutInfoProvider = remember(gridState, density) { SnapLayoutInfoProvider(gridState, density) }
    return rememberSnapFlingBehavior(snapLayoutInfoProvider)
}
