@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package tk.zwander.common.customgrid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.layout.LazyLayoutItemAnimator
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.*
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
 * [coroutineScope] drives the [itemAnimator]'s placement/fade-in/fade-out animations, started by
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

    /**
     * Drives every `animateItem()` fade-in/placement/fade-out animation — see [measureSpannedGrid].
     * The same internal class `LazyGridState`/`LazyListState` use, so items removed from the
     * underlying data (not just scrolled out of view) get a real disappearance animation too,
     * via a retained [androidx.compose.ui.graphics.layer.GraphicsLayer] snapshot of their last
     * rendered frame — something not reproducible from outside `androidx.compose.foundation`.
     */
    internal val itemAnimator = LazyLayoutItemAnimator<SpannedGridMeasuredItem>()

    /**
     * Whether any visible item currently has an in-progress `animateItem()` placement animation,
     * per the last measure pass. Used by [ReorderableLazySpannedGridState.chooseDropItem] to hold
     * off confirming another reorder until a previous cascade's animations have settled.
     */
    internal var hasActiveAnimations: Boolean by mutableStateOf(false)

    /**
     * The key of the item currently being drag-reordered (see
     * [ReorderableLazySpannedGridState]/`rememberReorderableLazySpannedGridState`'s
     * `draggingItemKey` observer), or null when nothing is being dragged. [measureSpannedGrid]
     * uses this to skip the `animateItem()` placement *animation* for exactly this key while it's
     * set — org.burnoutcrew.reorderable's own `ReorderableItem` draws the dragged item with a
     * `graphicsLayer` translation computed as (finger delta) − (this item's *current, unanimated*
     * grid slot position); if that slot position instead lags behind on a spring — which is what
     * `animateItem()` would otherwise do the moment a drag-triggered repack changes its target —
     * the two disagree for the length of the spring, and the dragged item visibly lurches back
     * towards its old slot before catching up to the pointer again. Only this one key's animation
     * is skipped; every other item that shifts to make room for the drag still animates normally.
     */
    internal var suppressPlacementAnimationKey: Any? by mutableStateOf(null)

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

    /**
     * [currentScrollOffsetPx] as of the last measure pass. Used to derive scroll direction for
     * prefetching between passes, and — read *before* [measureSpannedGrid]'s `schedulePrefetch`
     * call updates it — to compute [LazyLayoutItemAnimator.onMeasured]'s `consumedScroll`.
     */
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

    // Modifier.scrollableArea() (see LazySpannedGrid.kt) already inverts+RTL-corrects the raw drag
    // delta before it reaches here, so a positive delta simply means "scroll forward" (deeper into
    // content) — it's added to, not subtracted from, the forward scroll offset. Every other place
    // that manually feeds a delta into scrollBy()/scroll{} (ReorderableLazySpannedGridState's
    // autoscroll, WidgetGrid's interceptUnclaimedDrags, SpannedGridSnapping) is written against
    // this exact same convention — keep them all in sync if this ever changes again.
    private val internalScrollableState = ScrollableState { delta ->
        val oldOffset = scrollOffsetPx
        val target = (oldOffset + delta).coerceIn(0f, maxScrollOffsetPx)
        scrollOffsetPx = target
        target - oldOffset
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
        // scrollBy's delta is added to scrollOffsetPx (see internalScrollableState), so the delta
        // that moves scrollOffsetPx towards the target is simply target minus current.
        scroll { scrollBy(targetOffsetPx(line, scrollOffset) - scrollOffsetPx) }
    }

    /** Animates the scroll position so that [line] becomes the first visible line. */
    suspend fun animateScrollToLine(line: Int, scrollOffset: Int = 0) {
        animateScrollBy(targetOffsetPx(line, scrollOffset) - scrollOffsetPx)
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
