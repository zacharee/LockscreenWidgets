@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package tk.zwander.common.customgrid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.roundToInt

/**
 * Caches the last [SpannedGridPlacementResult] so pure scrolling never re-runs the bin-packer.
 * Shared by [LazyVerticalSpannedGrid] and [LazyHorizontalSpannedGrid].
 */
internal class SpannedGridPlacementCache {
    private var lastItemProvider: LazySpannedGridItemProvider? = null
    private var lastCrossAxisCount = -1
    private var lastMinLineCount = -1
    private var lastSignature: IntArray? = null
    private var lastResult: SpannedGridPlacementResult? = null

    fun get(
        itemProvider: LazySpannedGridItemProvider,
        crossAxisCount: Int,
        minLineCount: Int,
        crossAxisSpanOf: (Int) -> Int,
        mainAxisSpanOf: (Int) -> Int,
    ): SpannedGridPlacementResult {
        val cached = lastResult
        val itemCount = itemProvider.itemCount

        // rememberLazySpannedGridItemProviderLambda's derivedStateOf returns the exact same
        // itemProvider instance across pure-scroll measure passes — it only changes identity when
        // the caller's content actually produces different items — so a reference check alone
        // lets pure scrolling skip rebuilding the itemCount-sized signature array below entirely.
        if (cached != null &&
            itemProvider === lastItemProvider &&
            crossAxisCount == lastCrossAxisCount &&
            minLineCount == lastMinLineCount
        ) {
            return cached
        }

        val signature =
            IntArray(itemCount) { index -> crossAxisSpanOf(index) * 1_000 + mainAxisSpanOf(index) }
        if (cached != null &&
            crossAxisCount == lastCrossAxisCount &&
            minLineCount == lastMinLineCount &&
            signature.contentEquals(lastSignature)
        ) {
            // Spans/order genuinely didn't change even though the provider instance did (e.g. a
            // recomposition that rebuilt the same content) — keep the cached result, but adopt
            // the new instance so the cheap reference check above can fire next time.
            lastItemProvider = itemProvider
            return cached
        }
        val result =
            computeSpannedGridPlacement(itemCount, crossAxisCount, minLineCount) { index ->
                SpannedGridItemSpan(columnSpan = crossAxisSpanOf(index), rowSpan = mainAxisSpanOf(index))
            }
        lastItemProvider = itemProvider
        lastCrossAxisCount = crossAxisCount
        lastMinLineCount = minLineCount
        lastSignature = signature
        lastResult = result
        return result
    }
}

/**
 * The orientation-aware measure/placement logic shared by [LazyVerticalSpannedGrid] and
 * [LazyHorizontalSpannedGrid].
 *
 * Internally, [computeSpannedGridPlacement] always thinks in terms of a bounded "column" (cross)
 * axis and an unbounded, scrollable "row" (main) axis. For a vertical grid that maps 1:1 onto
 * visual rows/columns; for a horizontal grid, cross axis = visual rows and main axis = visual
 * columns, so item spans and the resulting placement are swapped going in and coming out.
 *
 * Also drives [LazySpannedGridState.itemAnimator] (the same internal
 * `androidx.compose.foundation.lazy.layout.LazyLayoutItemAnimator` stock `LazyGrid`/`LazyList`
 * use) so items whose grid position changed since the last pass animate their offset, newly
 * appearing items fade in, and items no longer naturally visible — whether they scrolled out or
 * were actually removed from the data — fade out via a retained `GraphicsLayer` snapshot of their
 * last rendered frame. See the KDoc on [LazySpannedGridItemScope.animateItem].
 *
 * @param crossAxisCount fixed number of lanes perpendicular to the scroll direction (columns for
 *   vertical, rows for horizontal).
 * @param mainAxisLineCount reference/minimum number of lines along the scroll direction (rows for
 *   vertical, columns for horizontal) used to compute a fixed line size. If the packed items need
 *   more lines than this, the grid simply becomes scrollable past it.
 */
internal fun measureSpannedGrid(
    measureScope: LazyLayoutMeasureScope,
    itemProvider: LazySpannedGridItemProvider,
    state: LazySpannedGridState,
    orientation: Orientation,
    crossAxisCount: Int,
    mainAxisLineCount: Int,
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    constraints: Constraints,
    placementCache: SpannedGridPlacementCache,
    graphicsContext: GraphicsContext,
): MeasureResult =
    with(measureScope) {
        val isVertical = orientation == Orientation.Vertical

        // "logical" horizontal padding — start is the left edge in LTR, right edge in RTL. This
        // is intentional: item x-positions below are placed with placeRelative, which itself
        // mirrors logical positions for RTL, so padding must stay in the same logical space.
        val logicalStartPaddingPx = contentPadding.calculateStartPadding(layoutDirection).roundToPx()
        val logicalEndPaddingPx = contentPadding.calculateEndPadding(layoutDirection).roundToPx()
        val topPaddingPx = contentPadding.calculateTopPadding().roundToPx()
        val bottomPaddingPx = contentPadding.calculateBottomPadding().roundToPx()

        val mainAxisStartPad = if (isVertical) topPaddingPx else logicalStartPaddingPx
        val mainAxisEndPad = if (isVertical) bottomPaddingPx else logicalEndPaddingPx
        val crossAxisStartPad = if (isVertical) logicalStartPaddingPx else topPaddingPx
        val crossAxisEndPad = if (isVertical) logicalEndPaddingPx else bottomPaddingPx

        val viewportMainAxisPx = if (isVertical) constraints.maxHeight else constraints.maxWidth
        val viewportCrossAxisPx = if (isVertical) constraints.maxWidth else constraints.maxHeight
        val availableMainAxisPx = (viewportMainAxisPx - mainAxisStartPad - mainAxisEndPad).coerceAtLeast(0)
        val availableCrossAxisPx = (viewportCrossAxisPx - crossAxisStartPad - crossAxisEndPad).coerceAtLeast(0)

        val viewportWidthPx = if (isVertical) viewportCrossAxisPx else viewportMainAxisPx
        val viewportHeightPx = if (isVertical) viewportMainAxisPx else viewportCrossAxisPx

        val crossAxisLineSizePx = availableCrossAxisPx / crossAxisCount
        val mainAxisLineSizePx = availableMainAxisPx / mainAxisLineCount

        val itemCount = itemProvider.itemCount
        val placementResult =
            placementCache.get(
                itemProvider = itemProvider,
                crossAxisCount = crossAxisCount,
                minLineCount = mainAxisLineCount,
                crossAxisSpanOf = { index ->
                    val span = itemProvider.spanOf(index)
                    if (isVertical) span.columnSpan else span.rowSpan
                },
                mainAxisSpanOf = { index ->
                    val span = itemProvider.spanOf(index)
                    if (isVertical) span.rowSpan else span.columnSpan
                },
            )
        state.latestPlacementResult = placementResult

        state.mainAxisLineCount = mainAxisLineCount
        state.crossAxisLineCount = crossAxisCount

        val contentMainAxisPx =
            placementResult.totalRowCount * mainAxisLineSizePx + mainAxisStartPad + mainAxisEndPad
        state.applyMeasureResult(
            lineSizePx = mainAxisLineSizePx,
            viewportMainAxisPx = viewportMainAxisPx,
            contentMainAxisPx = contentMainAxisPx,
        )

        // Read before schedulePrefetch (below) overwrites it — both it and the itemAnimator's
        // consumedScroll want "the scroll offset as of the last measure pass".
        val previousScrollOffsetPx = state.lastScrollOffsetPxForPrefetch
        val scrollOffsetPx = state.currentScrollOffsetPx.toInt()

        val firstVisibleLine =
            if (mainAxisLineSizePx <= 0) {
                0
            } else {
                (scrollOffsetPx / mainAxisLineSizePx).coerceIn(0, placementResult.totalRowCount - 1)
            }
        val lastVisibleLine =
            if (mainAxisLineSizePx <= 0) {
                placementResult.totalRowCount - 1
            } else {
                ((scrollOffsetPx + availableMainAxisPx) / mainAxisLineSizePx)
                    .coerceIn(firstVisibleLine, placementResult.totalRowCount - 1)
            }

        // Reused across measure passes instead of allocating a fresh BooleanArray(itemCount) every
        // scroll frame — only grown, never shrunk, and only the used prefix is cleared.
        if (state.visitedIndicesScratch.size < itemCount) {
            state.visitedIndicesScratch = BooleanArray(itemCount)
        } else {
            state.visitedIndicesScratch.fill(false, 0, itemCount)
        }
        val visitedIndices = state.visitedIndicesScratch

        // Widen the rendered-line range (only for building the render set below — NOT
        // firstVisibleLine/lastVisibleLine themselves, which stay viewport-accurate for scroll
        // position/prefetch-direction math) to always cover the currently-dragged item's own
        // lines, however far autoscroll has carried the viewport past them. Otherwise, once
        // autoscrolling drags it further than a page away, it drops out of the naturally-visible
        // set entirely and its LazyLayout composition slot gets torn down and recreated — which
        // re-fires ReorderableItem's `LaunchedEffect(isDragging)` in WidgetGrid.kt on the fresh
        // instance, and since that effect *toggles* `currentEditingId` (on the assumption
        // isDragging can only turn true once per drag), the second firing flips it back to
        // NO_POSITION. That immediately un-gates `interceptUnclaimedDrags`'s own guard against
        // stealing an in-progress reorder drag's touch, which then does exactly that on the next
        // qualifying move — and the reorder library sees its tracked pointer consumed elsewhere
        // and cancels the drag. Confirmed via logcat: the drag's onDragCanceled always landed in
        // the same frame the item left the natural window, every time, across repeated drags.
        val draggedLineRange =
            state.suppressPlacementAnimationKey?.let { key ->
                val index = itemProvider.getIndex(key)
                if (index >= 0) placementResult.placements.getOrNull(index) else null
            }?.let { placement -> placement.row..(placement.row + placement.rowSpan - 1) }
        val renderFirstLine = if (draggedLineRange != null) minOf(firstVisibleLine, draggedLineRange.first) else firstVisibleLine
        val renderLastLine = if (draggedLineRange != null) maxOf(lastVisibleLine, draggedLineRange.last) else lastVisibleLine

        val naturalVisibleIndices = ArrayList<Int>()
        if (placementResult.totalRowCount > 0) {
            for (line in renderFirstLine..renderLastLine) {
                for (index in placementResult.rowToItemIndices[line]) {
                    if (!visitedIndices[index]) {
                        visitedIndices[index] = true
                        naturalVisibleIndices.add(index)
                    }
                }
            }
        }

        schedulePrefetch(
            state = state,
            placementResult = placementResult,
            renderIndices = naturalVisibleIndices.toHashSet(),
            firstVisibleLine = firstVisibleLine,
            lastVisibleLine = lastVisibleLine,
            mainAxisLineSizePx = mainAxisLineSizePx,
            crossAxisLineSizePx = crossAxisLineSizePx,
            isVertical = isVertical,
        )

        val measuredItemProvider = SpannedGridMeasuredItemProvider(itemProvider, measureScope, state.itemAnimator)

        val positionedItems = ArrayList<SpannedGridMeasuredItem>(naturalVisibleIndices.size)
        val visibleItemsInfo = ArrayList<LazySpannedGridItemInfo>(naturalVisibleIndices.size)

        for (index in naturalVisibleIndices) {
            val placement = placementResult.placements[index]

            // placement.column/row are always the bounded cross-axis / unbounded main-axis
            // indices; map them back to visual row/column based on orientation.
            val visualRow = if (isVertical) placement.row else placement.column
            val visualColumn = if (isVertical) placement.column else placement.row
            val visualRowSpan = if (isVertical) placement.rowSpan else placement.columnSpan
            val visualColumnSpan = if (isVertical) placement.columnSpan else placement.rowSpan

            val mainAxisIndex = placement.row
            val crossAxisIndex = placement.column
            val mainAxisSpan = placement.rowSpan
            val crossAxisSpan = placement.columnSpan

            val itemMainAxisSizePx = mainAxisLineSizePx * mainAxisSpan
            val itemCrossAxisSizePx = crossAxisLineSizePx * crossAxisSpan

            val mainAxisContentPos = mainAxisStartPad + mainAxisIndex * mainAxisLineSizePx
            val crossAxisPos = crossAxisStartPad + crossAxisIndex * crossAxisLineSizePx

            val contentX = if (isVertical) crossAxisPos else mainAxisContentPos
            val contentY = if (isVertical) mainAxisContentPos else crossAxisPos
            val itemWidthPx = if (isVertical) itemCrossAxisSizePx else itemMainAxisSizePx
            val itemHeightPx = if (isVertical) itemMainAxisSizePx else itemCrossAxisSizePx

            // Final, screen-relative position — scroll is applied directly here (never itself
            // animated); the itemAnimator is told about this via consumedScroll below, so a pure
            // scroll never triggers its placement animation.
            val finalX = if (isVertical) contentX else contentX - scrollOffsetPx
            val finalY = if (isVertical) contentY - scrollOffsetPx else contentY

            val key = itemProvider.getKey(index)
            val itemConstraints = Constraints.fixed(itemWidthPx, itemHeightPx)
            val item =
                measuredItemProvider.getAndMeasure(
                    index = index,
                    lane = crossAxisIndex,
                    span = crossAxisSpan,
                    constraints = itemConstraints,
                )
            item.position(finalX, finalY, viewportWidthPx, viewportHeightPx)
            item.skipPlacementAnimation = key == state.suppressPlacementAnimationKey
            positionedItems.add(item)

            visibleItemsInfo.add(
                LazySpannedGridItemInfo(
                    index = index,
                    key = key,
                    row = visualRow,
                    column = visualColumn,
                    rowSpan = visualRowSpan,
                    columnSpan = visualColumnSpan,
                    offset = IntOffset(finalX, finalY),
                    size = IntSize(itemWidthPx, itemHeightPx),
                ),
            )
        }

        // See the derivation in the animateItem/itemAnimator KDoc above: screenOffset = contentOffset
        // - scrollOffsetPx, so the screen-relative delta attributable to scrolling alone is the
        // *negative* of the scroll delta.
        val consumedScrollPx = (previousScrollOffsetPx - state.currentScrollOffsetPx).roundToInt()

        // onMeasured may mutate positionedItems, appending items that just left the naturally
        // visible window (whether by scrolling or removal) so they can keep animating away —
        // resolved via keyIndexMap and re-measured at their last known constraints/lane/span.
        state.itemAnimator.onMeasured(
            consumedScroll = consumedScrollPx,
            layoutWidth = viewportWidthPx,
            layoutHeight = viewportHeightPx,
            positionedItems = positionedItems,
            keyIndexMap = itemProvider.keyIndexMap,
            itemProvider = measuredItemProvider,
            isVertical = isVertical,
            isLookingAhead = false,
            laneCount = crossAxisCount,
            hasLookaheadOccurred = false,
            layoutMinOffset = 0,
            layoutMaxOffset = viewportMainAxisPx,
            coroutineScope = state.coroutineScope,
            graphicsContext = graphicsContext,
        )

        // Excludes the currently-dragged key (if any): its own placement animation is never
        // rendered anyway (see skipPlacementAnimation above), so whether the animator personally
        // considers *it* still in progress is irrelevant to "let the last move's cascade settle
        // before confirming another" — only other items' (visible) animations matter for that.
        // Including it needlessly extended how long ReorderableLazySpannedGridState.chooseDropItem
        // stayed blocked, making the drop-target debounce miss more often on a normal-speed drag.
        val suppressedKey = state.suppressPlacementAnimationKey
        state.hasActiveAnimations =
            positionedItems.fastAny { item ->
                item.key != suppressedKey &&
                    item.placeables.indices.any { placeableIndex ->
                        state.itemAnimator.getAnimation(item.key, placeableIndex)?.isPlacementAnimationInProgress == true
                    }
            }

        val cellWidthPx = if (isVertical) crossAxisLineSizePx else mainAxisLineSizePx
        val cellHeightPx = if (isVertical) mainAxisLineSizePx else crossAxisLineSizePx

        state.layoutInfo =
            LazySpannedGridLayoutInfo(
                visibleItemsInfo = visibleItemsInfo,
                totalLineCount = placementResult.totalRowCount,
                viewportSize = IntSize(viewportWidthPx, viewportHeightPx),
                cellSize = IntSize(cellWidthPx, cellHeightPx),
                orientation = orientation,
            )

        layout(viewportWidthPx, viewportHeightPx) {
            positionedItems.fastForEach { it.place(this, isLookingAhead = false) }
        }
    }

/**
 * Schedules precomposition/premeasure (see [LazyLayoutPrefetchState]) for the main-axis line just
 * beyond the currently-visible range, in whichever direction the grid is scrolling — mirroring
 * Compose Foundation's own `DefaultLazyGridPrefetchStrategy`, simplified since every line here is
 * a fixed, known pixel size and [SpannedGridPlacementResult.rowToItemIndices] already gives us
 * every item index touching a line directly.
 *
 * Scroll direction is derived from the change in [LazySpannedGridState.currentScrollOffsetPx]
 * since the last measure pass, since — unlike `LazyGridState.onScroll` — this grid doesn't have a
 * separate raw-delta callback to hook into; scheduling from the tail of a measure pass mirrors how
 * the reference implementation calls into prefetch scheduling right after applying a measure
 * result (see `LazyGridState.applyMeasureResult`/`notifyPrefetchOnScroll`).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun schedulePrefetch(
    state: LazySpannedGridState,
    placementResult: SpannedGridPlacementResult,
    renderIndices: Set<Int>,
    firstVisibleLine: Int,
    lastVisibleLine: Int,
    mainAxisLineSizePx: Int,
    crossAxisLineSizePx: Int,
    isVertical: Boolean,
) {
    val prefetchState = state.prefetchState ?: return
    if (mainAxisLineSizePx <= 0 || crossAxisLineSizePx <= 0) return

    val scrollOffsetPx = state.currentScrollOffsetPx
    val delta = scrollOffsetPx - state.lastScrollOffsetPxForPrefetch
    state.lastScrollOffsetPxForPrefetch = scrollOffsetPx

    val targetLine =
        when {
            delta > 0f -> lastVisibleLine + 1
            delta < 0f -> firstVisibleLine - 1
            else -> -1
        }

    if (targetLine < 0 || targetLine >= placementResult.totalRowCount) {
        // Not scrolling, or already at the edge in this direction — nothing new to prefetch.
        if (state.prefetchedLine != -1) {
            state.prefetchHandles.forEach { it.cancel() }
            state.prefetchHandles = emptyList()
            state.prefetchedLine = -1
        }
        return
    }

    if (targetLine == state.prefetchedLine) return // already scheduled

    state.prefetchHandles.forEach { it.cancel() }
    state.prefetchedLine = targetLine

    val handles = ArrayList<LazyLayoutPrefetchState.PrefetchHandle>()
    for (index in placementResult.rowToItemIndices[targetLine]) {
        if (index in renderIndices) continue // already visible/composed this pass
        val placement = placementResult.placements[index]
        val itemMainAxisSizePx = mainAxisLineSizePx * placement.rowSpan
        val itemCrossAxisSizePx = crossAxisLineSizePx * placement.columnSpan
        val widthPx = if (isVertical) itemCrossAxisSizePx else itemMainAxisSizePx
        val heightPx = if (isVertical) itemMainAxisSizePx else itemCrossAxisSizePx
        handles += prefetchState.schedulePrecompositionAndPremeasure(index, Constraints.fixed(widthPx, heightPx))
    }
    state.prefetchHandles = handles
}
