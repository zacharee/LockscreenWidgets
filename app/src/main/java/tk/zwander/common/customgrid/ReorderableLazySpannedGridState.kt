@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE")
package tk.zwander.common.customgrid

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.burnoutcrew.reorderable.DragCancelledAnimation
import org.burnoutcrew.reorderable.ItemPosition
import org.burnoutcrew.reorderable.ReorderableState
import org.burnoutcrew.reorderable.SpringDragCancelledAnimation

@Composable
fun rememberReorderableLazySpannedGridState(
    onMove: (ItemPosition, ItemPosition) -> Unit,
    gridState: LazySpannedGridState = rememberLazySpannedGridState(),
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> (Unit))? = null,
    maxScrollPerFrame: Dp = 20.dp,
    /** How close to either end of the viewport the dragged item has to get before autoscrolling starts. */
    edgeScrollMargin: Dp = 56.dp,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation()
): ReorderableLazySpannedGridState {
    val density = LocalDensity.current
    val maxScrollPx = with(density) { maxScrollPerFrame.toPx() }
    val edgeScrollMarginPx = with(density) { edgeScrollMargin.toPx() }
    val scope = rememberCoroutineScope()
    val state = remember(gridState) {
        ReorderableLazySpannedGridState(gridState, scope, maxScrollPx, onMove, canDragOver, onDragEnd, dragCancelledAnimation)
    }
    LaunchedEffect(state) {
        state.visibleItemsChanged()
            .collect { state.onDrag(0, 0) }
    }

    // Reset the drop-target debounce (see ReorderableLazySpannedGridState.chooseDropItem) on every
    // drag start, so it doesn't carry state over from a previous drag. draggingItemIndex is
    // public, unlike onDragStart/onDragCanceled, so this is observed rather than overridden.
    //
    // NOTE: a packed-geometry freeze (reusing each index's pre-drag row/column/span while
    // dragging, only repacking on drop) used to live here to stop a live reorder's repack from
    // cascading and causing jitter. It's gone for good this time, not just removed to isolate
    // another fix: with heterogeneous item spans, freezing by index means whichever item now
    // occupies index i after a reorder renders at index i's *frozen* span, not its own — items
    // visibly inherit the size of whatever used to be in the slot they moved into. A valid tiling
    // after a reorder fundamentally requires a real repack; see chooseDropItem below for how
    // cascading jitter is instead addressed without freezing geometry.
    LaunchedEffect(state) {
        snapshotFlow { state.draggingItemIndex != null }
            .distinctUntilChanged()
            .collect { dragging -> if (dragging) state.resetDropTargetDebounce() }
    }

    // Autoscroll while the dragged item is within `edgeScrollMargin` of either end of the
    // viewport, not just once it's fully past it. ReorderableState's own built-in autoscroll
    // (driven by scrollChannel, which this deliberately never drains — trySend on it silently
    // no-ops with nothing receiving) only reacts once the dragged item's edge has actually crossed
    // the viewport boundary, and its threshold/ramp logic is private, so it can't be tuned by
    // overriding; this drives scrolling directly instead, with a margin that starts before the
    // edge is reached.
    LaunchedEffect(state) {
        while (true) {
            withFrameMillis {}
            val index = state.draggingItemIndex
            val info = index?.let { i -> gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i } }
            if (info == null) continue
            val isVertical = gridState.layoutInfo.orientation == Orientation.Vertical
            val viewportSize = if (isVertical) gridState.layoutInfo.viewportSize.height else gridState.layoutInfo.viewportSize.width
            val itemStart = (if (isVertical) info.offset.y else info.offset.x) +
                (if (isVertical) state.draggingItemTop else state.draggingItemLeft)
            val itemEnd = itemStart + (if (isVertical) info.size.height else info.size.width)
            val intoStartMargin = edgeScrollMarginPx - itemStart
            val intoEndMargin = itemEnd - (viewportSize - edgeScrollMarginPx)
            // gridState.scrollBy's delta is subtracted from its scroll offset (see
            // LazySpannedGridState.internalScrollableState), so decreasing the offset — scrolling
            // towards the start, for the top/left margin — takes a positive delta, and increasing
            // it — scrolling towards the end — takes a negative one.
            val scroll = when {
                intoStartMargin > 0f -> intoStartMargin.coerceAtMost(maxScrollPx)
                intoEndMargin > 0f -> -intoEndMargin.coerceAtMost(maxScrollPx)
                else -> 0f
            }
            if (scroll != 0f) gridState.scrollBy(scroll)
        }
    }
    return state
}

class ReorderableLazySpannedGridState(
    val gridState: LazySpannedGridState,
    scope: CoroutineScope,
    maxScrollPerFrame: Float,
    onMove: (fromIndex: ItemPosition, toIndex: ItemPosition) -> Unit,
    // Also kept as a property here (the base class's own copy is private, so it's otherwise
    // inaccessible) since our findTargets override below needs it and can't call super.findTargets.
    private val canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> (Unit))? = null,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
) : ReorderableState<LazySpannedGridItemInfo>(
    scope = scope,
    maxScrollPerFrame = maxScrollPerFrame,
    onMove = onMove,
    canDragOver = canDragOver,
    onDragEnd = onDragEnd,
    dragCancelledAnimation = dragCancelledAnimation,
) {
    override val firstVisibleItemIndex: Int
        get() = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    override val firstVisibleItemScrollOffset: Int
        get() = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.let {
            if (isVerticalScroll) {
                it.y
            } else {
                it.x
            }
        } ?: 0
    override val isVerticalScroll: Boolean
        get() = gridState.layoutInfo.orientation == Orientation.Vertical
    override val viewportEndOffset: Int
        get() = gridState.layoutInfo.viewportSize.let {
            if (isVerticalScroll) {
                it.height
            } else {
                it.width
            }
        }
    override val viewportStartOffset: Int
        get() = 0
    override val visibleItemsInfo: List<LazySpannedGridItemInfo>
        get() = gridState.layoutInfo.visibleItemsInfo
    override val LazySpannedGridItemInfo.itemIndex: Int
        get() = index
    override val LazySpannedGridItemInfo.itemKey: Any
        get() = key
    override val LazySpannedGridItemInfo.left: Int
        get() = offset.x
    override val LazySpannedGridItemInfo.right: Int
        get() = offset.x + size.width
    override val LazySpannedGridItemInfo.top: Int
        get() = offset.y
    override val LazySpannedGridItemInfo.bottom: Int
        get() = offset.y + size.height
    override val LazySpannedGridItemInfo.width: Int
        get() = size.width
    override val LazySpannedGridItemInfo.height: Int
        get() = size.height

    override suspend fun scrollToItem(index: Int, offset: Int) {
        gridState.scrollToItem(index, offset)
    }

    // The base implementation admits any candidate whose *rect* overlaps the dragged item's own
    // (original-sized) projected rect. With spans this varied — a 1x1 next to a 5x4 — that's an
    // unstable candidate set on its own, independent of how chooseDropItem later picks among them:
    // a giant item overlaps almost anywhere nearby regardless of how far its actual center is, so
    // it can contend for "target" well outside where it visually sits. Point-testing the dragged
    // item's own current *center* against each candidate's rect instead ties targeting to "which
    // single cell is the pointer physically over" — there's normally at most one, since the grid
    // is a non-overlapping tiling — which is what a drag-to-reorder grid is expected to feel like.
    override fun findTargets(
        x: Int,
        y: Int,
        selected: LazySpannedGridItemInfo,
    ): List<LazySpannedGridItemInfo> {
        val centerX = x + (selected.left + selected.right) / 2
        val centerY = y + (selected.top + selected.bottom) / 2
        return visibleItemsInfo.filter { item ->
            item.itemIndex != draggingItemIndex &&
                centerX in item.left..item.right &&
                centerY in item.top..item.bottom &&
                canDragOver?.invoke(
                    ItemPosition(item.itemIndex, item.itemKey),
                    ItemPosition(selected.itemIndex, selected.itemKey),
                ) != false
        }
    }

    // findTargets now returns at most one genuine candidate (see above), so this debounces that
    // single choice rather than picking among several: a candidate only actually triggers a move
    // once it's been the sole candidate for two calls in a row ([pendingTargetKey] tracks a
    // not-yet-confirmed challenger). Until it's confirmed, this returns null — no move this frame.
    // This guards against flicker right at a cell boundary, where the dragged item's center can
    // toggle between two adjacent cells on either side of a single pixel.
    //
    // Deliberately no "already-acted-on" blacklist beyond that: findTargets already excludes the
    // dragged item's own *current* slot by index, so once a move commits there's nothing left to
    // suppress — re-hovering a target you already swapped with (e.g. dragging back to undo) is a
    // perfectly normal, different candidate at that point, not a repeat of the same one. An
    // earlier version tracked a permanent "committedTargetKey" here to be extra sure a settled
    // target wouldn't immediately re-trigger, but that also blocked ever re-selecting it again
    // later in the same drag — making it impossible to drag an item back to cancel a reorder.
    private var pendingTargetKey: Any? = null

    /** Called on every drag start (see [rememberReorderableLazySpannedGridState]) so a new drag doesn't inherit debounce state left over from a previous one. */
    internal fun resetDropTargetDebounce() {
        pendingTargetKey = null
    }

    override fun chooseDropItem(
        draggedItemInfo: LazySpannedGridItemInfo?,
        items: List<LazySpannedGridItemInfo>,
        curX: Int,
        curY: Int,
    ): LazySpannedGridItemInfo? {
        if (draggedItemInfo == null) {
            return if (draggingItemIndex != null) items.lastOrNull() else null
        }
        // A confirmed move triggers a real repack, which can cascade and give several items a
        // fresh animateItem() placement animation (see the NOTE above resetDropTargetDebounce).
        // Those animations run on gridState.coroutineScope, independent of this debounce, so
        // without this check a second move can confirm — and retarget those same animations
        // mid-flight — before the first one's springs have settled. That's what visually reads as
        // "jiggling": items sliding partway toward one spot, then snapping toward another. Holding
        // off until gridState.animatedOffsets is empty lets each cascade finish before the next
        // one can start; pendingTargetKey is deliberately left untouched while held off, so once
        // unblocked, an unchanged candidate confirms immediately rather than re-debouncing.
        if (gridState.animatedOffsets.isNotEmpty()) {
            return null
        }
        val candidate = items.firstOrNull()
        if (candidate == null) {
            pendingTargetKey = null
            return null
        }
        if (candidate.itemKey == pendingTargetKey) {
            pendingTargetKey = null
            return candidate
        }
        pendingTargetKey = candidate.itemKey
        return null
    }

    fun onDrag(x: Int, y: Int) {
        super.onDrag(x, y)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun visibleItemsChanged() =
        snapshotFlow { draggingItemIndex != null }
        .flatMapLatest { if (it) snapshotFlow { visibleItemsInfo } else flowOf(null) }
        .filterNotNull()
        .distinctUntilChanged { old, new -> old.firstOrNull()?.itemIndex == new.firstOrNull()?.itemIndex && old.count() == new.count() }
}
