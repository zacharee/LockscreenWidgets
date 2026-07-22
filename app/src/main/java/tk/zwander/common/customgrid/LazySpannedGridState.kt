package tk.zwander.common.customgrid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

/** Creates a [LazySpannedGridState] that survives recomposition. */
@Composable
fun rememberLazySpannedGridState(
    initialFirstVisibleLine: Int = 0,
    initialScrollOffset: Int = 0,
): LazySpannedGridState {
    val coroutineScope = rememberCoroutineScope()
    return remember { LazySpannedGridState(coroutineScope, initialFirstVisibleLine, initialScrollOffset) }
}

/** The [FiniteAnimationSpec]s an item last registered via [LazySpannedGridItemScope.animateItem]. */
internal data class ItemAnimationSpecs(
    val placementSpec: FiniteAnimationSpec<IntOffset>?,
    val fadeOutSpec: FiniteAnimationSpec<Float>?,
)

/**
 * State of a [LazyVerticalSpannedGrid] or [LazyHorizontalSpannedGrid], tracking the scroll
 * position along the main (scrollable) axis in "lines" — a row for the vertical grid, a column
 * for the horizontal one — plus the layout info produced by the most recent measure pass.
 *
 * This class is orientation-agnostic: it only deals with a generic main-axis "line" and doesn't
 * know whether that maps to rows or columns. Both grids share the exact same state and measure
 * logic (see [measureSpannedGrid]).
 *
 * Scroll position is stored internally in pixels; [firstVisibleLine]/[firstVisibleLineScrollOffset]
 * are derived from it using the line size discovered during the last measure pass (mirroring how
 * `LazyGridState` derives its item-based position from pixel offsets known only after measuring).
 *
 * [coroutineScope] drives the placement/fade-out animations started by
 * [LazySpannedGridItemScope.animateItem] (see [measureSpannedGrid]) — it's cancelled, and any
 * in-flight animations with it, once the grid leaves composition.
 */
class LazySpannedGridState(
    internal val coroutineScope: CoroutineScope,
    initialFirstVisibleLine: Int = 0,
    initialScrollOffset: Int = 0,
) : ScrollableState {
    private var pendingInitialOffset = true
    private val initialFirstVisibleLine = initialFirstVisibleLine.coerceAtLeast(0)
    private val initialScrollOffset = initialScrollOffset.coerceAtLeast(0)

    private var scrollOffsetPx by mutableFloatStateOf(0f)
    private var maxScrollOffsetPx by mutableFloatStateOf(0f)

    internal var mainAxisLineCount by mutableIntStateOf(1)
    internal var crossAxisLineCount by mutableIntStateOf(1)

    internal var lineSizePx: Int = 0
        private set

    var layoutInfo: LazySpannedGridLayoutInfo by mutableStateOf(LazySpannedGridLayoutInfo.Empty)
        internal set

    /** The [FiniteAnimationSpec]s each item (by key) last registered via `animateItem`. */
    internal val itemAnimationSpecs = mutableMapOf<Any, ItemAnimationSpecs>()

    /** Items (by key) currently animating from their previous grid position to a new one. */
    internal val animatedOffsets = mutableStateMapOf<Any, Animatable<IntOffset, AnimationVector2D>>()

    /** Items (by key) currently fading out after scrolling outside the visible viewport. */
    internal val fadeOutAlphas = mutableStateMapOf<Any, Animatable<Float, AnimationVector1D>>()

    /**
     * The target (un-animated) *content-relative* position of each visible item (by key) from the
     * last measure pass — i.e. before the scroll offset is subtracted. Deliberately not the final
     * screen position: comparing screen-relative targets across measure passes would make every
     * item look like it moved on every single scroll frame (since scrolling shifts everyone's
     * screen position), spuriously re-triggering the `animateItem()` placement animation and
     * making scrolling permanently lag behind the finger instead of tracking it immediately. See
     * the comment in [measureSpannedGrid] where this is compared against the new target.
     */
    internal var lastPlacedTargets: Map<Any, IntOffset> = emptyMap()

    /** The keys naturally visible (i.e. not force-included for a fade-out) in the last measure pass. */
    internal var lastVisibleKeys: Set<Any> = emptySet()

    /**
     * The full (not just currently-visible) item placement from the last measure pass, used by
     * [scrollToItem]/[animateScrollToItem] to resolve an arbitrary, possibly off-screen, item
     * index to its main-axis line.
     */
    internal var latestPlacementResult: SpannedGridPlacementResult? = null

    /**
     * Set once, from [LazyVerticalSpannedGrid]/[LazyHorizontalSpannedGrid], and used by
     * [measureSpannedGrid] to schedule precomposition/premeasure of the line just beyond the
     * visible range in the scroll direction — see the prefetch bookkeeping fields below.
     */
    @OptIn(ExperimentalFoundationApi::class)
    internal var prefetchState: LazyLayoutPrefetchState? = null

    /** The main-axis line last scheduled for prefetch, or -1 if none. Mirrors DefaultLazyGridPrefetchStrategy's `lineToPrefetch`. */
    internal var prefetchedLine: Int = -1

    /** The still-outstanding prefetch handles for [prefetchedLine], cancelled once a different line should be prefetched instead. */
    @OptIn(ExperimentalFoundationApi::class)
    internal var prefetchHandles: List<LazyLayoutPrefetchState.PrefetchHandle> = emptyList()

    /** [currentScrollOffsetPx] as of the last measure pass, used to derive scroll direction for prefetching between passes. */
    internal var lastScrollOffsetPxForPrefetch: Float = 0f

    /** Scratch buffer reused across measure passes instead of allocating a fresh `BooleanArray(itemCount)` every scroll frame — see [measureSpannedGrid]. */
    internal var visitedIndicesScratch: BooleanArray = BooleanArray(0)

    /** Index of the first main-axis line (row for vertical, column for horizontal) at least partially visible. */
    val firstVisibleLine: Int
        get() = if (lineSizePx <= 0) 0 else (scrollOffsetPx / lineSizePx).toInt()

    /** Scroll offset, in pixels, of [firstVisibleLine] past the start of the viewport. */
    val firstVisibleLineScrollOffset: Int
        get() =
            if (lineSizePx <= 0) {
                0
            } else {
                (scrollOffsetPx - firstVisibleLine * lineSizePx).roundToInt()
            }

    internal val currentScrollOffsetPx: Float
        get() = scrollOffsetPx

    // Mirrors androidx.compose.foundation.samples.LazyLayoutScrollableSample: a positive scroll
    // delta (dragging towards the start) reduces how far forward we've scrolled, so it's
    // subtracted from, not added to, the forward scroll offset.
    private val internalScrollableState = ScrollableState { delta ->
        val oldOffset = scrollOffsetPx
        val target = (oldOffset - delta).coerceIn(0f, maxScrollOffsetPx)
        scrollOffsetPx = target
        oldOffset - target
    }

    /**
     * Called by [measureSpannedGrid] after every measure pass to reconcile the pixel scroll
     * position with the newly-known line/content/viewport sizes.
     */
    internal fun applyMeasureResult(lineSizePx: Int, viewportMainAxisPx: Int, contentMainAxisPx: Int) {
        if (pendingInitialOffset && lineSizePx > 0) {
            scrollOffsetPx = (initialFirstVisibleLine * lineSizePx + initialScrollOffset).toFloat()
            pendingInitialOffset = false
        }
        this.lineSizePx = lineSizePx
        maxScrollOffsetPx = (contentMainAxisPx - viewportMainAxisPx).coerceAtLeast(0).toFloat()
        scrollOffsetPx = scrollOffsetPx.coerceIn(0f, maxScrollOffsetPx)
    }

    /** Instantly scrolls so that [line] is the first visible line, offset by [scrollOffset] pixels. */
    suspend fun scrollToLine(line: Int, scrollOffset: Int = 0) {
        // scrollBy's delta is subtracted from scrollOffsetPx (see internalScrollableState), so to
        // move scrollOffsetPx towards the target we pass the delta with the sign flipped.
        scroll { scrollBy(scrollOffsetPx - targetOffsetPx(line, scrollOffset)) }
    }

    /** Animates the scroll position so that [line] becomes the first visible line. */
    suspend fun animateScrollToLine(line: Int, scrollOffset: Int = 0) {
        animateScrollBy(scrollOffsetPx - targetOffsetPx(line, scrollOffset))
    }

    /**
     * Instantly scrolls so that the item at [index] starts at [scrollOffset] pixels past the
     * start of the viewport. A no-op if [index] isn't a currently valid item, or if called before
     * the grid has completed its first measure pass (mirroring [scrollToLine]'s behavior then).
     */
    suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {
        val line = lineOfItemOrNull(index) ?: return
        scrollToLine(line, scrollOffset)
    }

    /**
     * Animates the scroll position so that the item at [index] starts at [scrollOffset] pixels
     * past the start of the viewport. A no-op if [index] isn't a currently valid item, or if
     * called before the grid has completed its first measure pass.
     */
    suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0) {
        val line = lineOfItemOrNull(index) ?: return
        animateScrollToLine(line, scrollOffset)
    }

    /** The main-axis line the item at [index] starts on, per the last measure pass, if known. */
    private fun lineOfItemOrNull(index: Int): Int? =
        latestPlacementResult?.placements?.getOrNull(index)?.row

    private fun targetOffsetPx(line: Int, scrollOffset: Int): Float =
        (line.coerceAtLeast(0) * lineSizePx + scrollOffset).toFloat().coerceIn(0f, maxScrollOffsetPx)

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) = internalScrollableState.scroll(scrollPriority, block)

    override fun dispatchRawDelta(delta: Float): Float =
        internalScrollableState.dispatchRawDelta(delta)

    override val isScrollInProgress: Boolean
        get() = internalScrollableState.isScrollInProgress

    override val canScrollForward: Boolean
        get() = scrollOffsetPx < maxScrollOffsetPx

    override val canScrollBackward: Boolean
        get() = scrollOffsetPx > 0f
}
