package tk.zwander.common.customgrid

/** Where a single item landed after [computeSpannedGridPlacement] has run. */
data class SpannedGridItemPlacement(
    val index: Int,
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val columnSpan: Int,
)

/**
 * The result of packing every item into the grid.
 *
 * @param placements One entry per item, in item-index order.
 * @param totalRowCount The number of rows actually needed, which may exceed the requested
 *   reference row count if the packed items overflow it.
 * @param rowToItemIndices For each row (`size == totalRowCount`), the indices of every item whose
 *   placement overlaps that row.
 */
data class SpannedGridPlacementResult(
    val placements: List<SpannedGridItemPlacement>,
    val totalRowCount: Int,
    val rowToItemIndices: List<IntArray>,
)

/**
 * Greedily packs [itemCount] items into a grid of [columnCount] columns, mirroring
 * `SpannedGridLayoutManager`'s placement algorithm: items are processed strictly in index order,
 * and each claims the first free rectangle (row-major scan) large enough for its span. This
 * order-dependence is intentional, matching the reference RecyclerView implementation.
 *
 * The main axis (rows) is unbounded and grows past [minRowCount] if the packed content needs it.
 */
fun computeSpannedGridPlacement(
    itemCount: Int,
    columnCount: Int,
    minRowCount: Int,
    spanForIndex: (index: Int) -> SpannedGridItemSpan,
): SpannedGridPlacementResult {
    require(columnCount > 0) { "columnCount must be positive, was $columnCount" }

    val occupancy = ArrayList<BooleanArray>()

    fun ensureRow(row: Int) {
        while (occupancy.size <= row) {
            occupancy.add(BooleanArray(columnCount))
        }
    }

    fun isFree(row: Int, column: Int, rowSpan: Int, columnSpan: Int): Boolean {
        for (r in row until row + rowSpan) {
            if (r >= occupancy.size) continue
            val occupiedRow = occupancy[r]
            for (c in column until column + columnSpan) {
                if (occupiedRow[c]) return false
            }
        }
        return true
    }

    fun occupy(row: Int, column: Int, rowSpan: Int, columnSpan: Int) {
        ensureRow(row + rowSpan - 1)
        for (r in row until row + rowSpan) {
            val occupiedRow = occupancy[r]
            for (c in column until column + columnSpan) {
                occupiedRow[c] = true
            }
        }
    }

    val placements = ArrayList<SpannedGridItemPlacement>(itemCount)

    for (index in 0 until itemCount) {
        val span = spanForIndex(index)
        val columnSpan = span.columnSpan.coerceIn(1, columnCount)
        val rowSpan = span.rowSpan.coerceAtLeast(1)

        var placedRow = -1
        var placedColumn = -1
        var row = 0
        while (placedRow < 0) {
            for (column in 0..columnCount - columnSpan) {
                if (isFree(row, column, rowSpan, columnSpan)) {
                    placedRow = row
                    placedColumn = column
                    break
                }
            }
            row++
        }

        occupy(placedRow, placedColumn, rowSpan, columnSpan)
        placements.add(
            SpannedGridItemPlacement(
                index = index,
                row = placedRow,
                column = placedColumn,
                rowSpan = rowSpan,
                columnSpan = columnSpan,
            ),
        )
    }

    return buildPlacementResult(placements, maxOf(minRowCount, occupancy.size))
}

/**
 * Derives [SpannedGridPlacementResult.totalRowCount]/[SpannedGridPlacementResult.rowToItemIndices]
 * from an already-decided [placements] list. Used by [computeSpannedGridPlacement] to finish
 * building its result once the greedy scan has placed every item.
 */
fun buildPlacementResult(
    placements: List<SpannedGridItemPlacement>,
    minRowCount: Int,
): SpannedGridPlacementResult {
    var maxRowExclusive = minRowCount
    for (placement in placements) {
        maxRowExclusive = maxOf(maxRowExclusive, placement.row + placement.rowSpan)
    }
    val rowToItemIndices = Array(maxRowExclusive) { ArrayList<Int>() }
    for (placement in placements) {
        for (r in placement.row until placement.row + placement.rowSpan) {
            rowToItemIndices[r].add(placement.index)
        }
    }

    return SpannedGridPlacementResult(
        placements = placements,
        totalRowCount = maxRowExclusive,
        rowToItemIndices = rowToItemIndices.map { it.toIntArray() },
    )
}
