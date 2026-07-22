package tk.zwander.common.customgrid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch

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
 * Also orchestrates the animations started by [LazySpannedGridItemScope.animateItem]: items
 * whose grid position changed since the last pass animate their offset (via
 * [LazySpannedGridState.animatedOffsets]), and items that scrolled outside the naturally visible
 * window — while still present in the data — are kept in the render set and faded out (via
 * [LazySpannedGridState.fadeOutAlphas]) instead of disappearing immediately. See the KDoc on
 * [LazySpannedGridItemScope.animateItem] for why items actually removed from the data can't be
 * animated this way.
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
        val naturalVisibleIndices = ArrayList<Int>()
        if (placementResult.totalRowCount > 0) {
            for (line in firstVisibleLine..lastVisibleLine) {
                for (index in placementResult.rowToItemIndices[line]) {
                    if (!visitedIndices[index]) {
                        visitedIndices[index] = true
                        naturalVisibleIndices.add(index)
                    }
                }
            }
        }

        // --- animateItem orchestration: fade-out ---
        // Figure out which items just left the naturally-visible window, and start fading out
        // any of them that registered a fadeOutSpec and are still present in the data (see the
        // limitation documented on LazySpannedGridItemScope.animateItem).
        val naturalVisibleKeys = HashSet<Any>(naturalVisibleIndices.size)
        for (index in naturalVisibleIndices) naturalVisibleKeys.add(itemProvider.getKey(index))

        // An item that became visible again (e.g. the user scrolled back) shouldn't keep fading.
        for (key in naturalVisibleKeys) state.fadeOutAlphas.remove(key)

        for (key in state.lastVisibleKeys) {
            if (key in naturalVisibleKeys || key in state.fadeOutAlphas) continue
            val fadeOutSpec = state.itemAnimationSpecs[key]?.fadeOutSpec ?: continue
            if (itemProvider.getIndex(key) < 0) continue // actually removed; can't keep composing it.
            val alpha = Animatable(1f)
            state.fadeOutAlphas[key] = alpha
            state.coroutineScope.launch {
                alpha.animateTo(0f, fadeOutSpec)
                state.fadeOutAlphas.remove(key)
            }
        }

        // Force-include still-fading items in the render set, resolving their (possibly shifted)
        // current index by key.
        val renderIndices = LinkedHashSet<Int>(naturalVisibleIndices)
        for (key in state.fadeOutAlphas.keys.toList()) {
            val index = itemProvider.getIndex(key)
            if (index < 0) {
                state.fadeOutAlphas.remove(key)
            } else {
                renderIndices.add(index)
            }
        }

        schedulePrefetch(
            state = state,
            placementResult = placementResult,
            renderIndices = renderIndices,
            firstVisibleLine = firstVisibleLine,
            lastVisibleLine = lastVisibleLine,
            mainAxisLineSizePx = mainAxisLineSizePx,
            crossAxisLineSizePx = crossAxisLineSizePx,
            isVertical = isVertical,
        )

        data class PlacedChild(val placeable: Placeable, val offset: IntOffset, val alpha: Float)

        val placedChildren = ArrayList<PlacedChild>(renderIndices.size)
        val visibleItemsInfo = ArrayList<LazySpannedGridItemInfo>(renderIndices.size)
        val newPlacedTargets = HashMap<Any, IntOffset>(renderIndices.size)

        for (index in renderIndices) {
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

            // Deliberately *not* scroll-adjusted — see below.
            val mainAxisContentPos = mainAxisStartPad + mainAxisIndex * mainAxisLineSizePx
            val crossAxisPos = crossAxisStartPad + crossAxisIndex * crossAxisLineSizePx

            val contentX = if (isVertical) crossAxisPos else mainAxisContentPos
            val contentY = if (isVertical) mainAxisContentPos else crossAxisPos
            val itemWidthPx = if (isVertical) itemCrossAxisSizePx else itemMainAxisSizePx
            val itemHeightPx = if (isVertical) itemMainAxisSizePx else itemCrossAxisSizePx

            val key = itemProvider.getKey(index)
            // Content-relative (i.e. with the scroll offset *not* subtracted yet) target, used
            // only to decide/drive the animateItem() placement animation. This has to exclude the
            // scroll offset: a screen-relative target changes on literally every scroll frame
            // (every visible item's screen position shifts as scrollOffsetPx changes), which —
            // when compared against the previous frame's screen-relative target — looked
            // indistinguishable from a genuine reorder and restarted the (deliberately gentle,
            // MediumLow-stiffness) placement spring every single frame. Since each restart chases
            // a target that's already moved on by the next frame, the rendered position could
            // never catch up to the finger during an active drag — not jank, a real, cumulative
            // lag. Comparing content-relative targets means a pure scroll (grid cell unchanged)
            // never triggers the animation at all, and the scroll offset is instead applied
            // immediately, additively, below — including on top of a genuine reorder animation
            // still in flight, so scrolling stays 1:1 even while an item is mid-animation.
            val contentTargetOffset = IntOffset(contentX, contentY)
            newPlacedTargets[key] = contentTargetOffset

            // --- animateItem orchestration: placement ---
            val placementSpec = state.itemAnimationSpecs[key]?.placementSpec
            val previousContentTarget = state.lastPlacedTargets[key]
            val contentRenderOffset: IntOffset
            if (placementSpec != null) {
                if (previousContentTarget != null && previousContentTarget != contentTargetOffset) {
                    val animatable =
                        state.animatedOffsets.getOrPut(key) {
                            Animatable(previousContentTarget, IntOffset.VectorConverter)
                        }
                    state.coroutineScope.launch {
                        animatable.animateTo(contentTargetOffset, placementSpec)
                        // Only clear if nothing re-targeted it again in the meantime.
                        if (animatable.value == contentTargetOffset) state.animatedOffsets.remove(key)
                    }
                }
                contentRenderOffset = state.animatedOffsets[key]?.value ?: contentTargetOffset
            } else {
                state.animatedOffsets.remove(key)
                contentRenderOffset = contentTargetOffset
            }

            // Scroll is applied here, immediately, after the animation decision above — never
            // itself animated.
            val renderOffset =
                if (isVertical) {
                    IntOffset(contentRenderOffset.x, contentRenderOffset.y - scrollOffsetPx)
                } else {
                    IntOffset(contentRenderOffset.x - scrollOffsetPx, contentRenderOffset.y)
                }

            val alpha = state.fadeOutAlphas[key]?.value ?: 1f

            val itemConstraints = Constraints.fixed(itemWidthPx, itemHeightPx)

            for (measurable in compose(index)) {
                placedChildren.add(PlacedChild(measurable.measure(itemConstraints), renderOffset, alpha))
            }
            visibleItemsInfo.add(
                LazySpannedGridItemInfo(
                    index = index,
                    key = key,
                    row = visualRow,
                    column = visualColumn,
                    rowSpan = visualRowSpan,
                    columnSpan = visualColumnSpan,
                    offset = renderOffset,
                    size = IntSize(itemWidthPx, itemHeightPx),
                ),
            )
        }

        state.lastVisibleKeys = naturalVisibleKeys
        state.lastPlacedTargets = newPlacedTargets

        val viewportWidthPx = if (isVertical) viewportCrossAxisPx else viewportMainAxisPx
        val viewportHeightPx = if (isVertical) viewportMainAxisPx else viewportCrossAxisPx
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
            placedChildren.forEach { (placeable, offset, alpha) ->
                if (alpha >= 1f) {
                    placeable.placeRelative(offset.x, offset.y)
                } else {
                    placeable.placeRelativeWithLayer(offset.x, offset.y) { this.alpha = alpha }
                }
            }
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
