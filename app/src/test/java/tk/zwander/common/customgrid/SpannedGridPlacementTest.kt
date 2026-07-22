package tk.zwander.common.customgrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpannedGridPlacementTest {
    private fun uniformSpan(index: Int) = SpannedGridItemSpan()

    private fun assertNoOverlaps(result: SpannedGridPlacementResult) {
        val occupied = HashSet<Long>()
        for (placement in result.placements) {
            for (row in placement.row until placement.row + placement.rowSpan) {
                for (column in placement.column until placement.column + placement.columnSpan) {
                    val cell = row.toLong() * 1_000_000 + column
                    assertTrue(
                        "Cell (row=$row, column=$column) occupied by more than one item " +
                            "(clashing at item ${placement.index})",
                        occupied.add(cell),
                    )
                }
            }
        }
    }

    @Test
    fun `uniform 1x1 items fill rows left to right`() {
        val result = computeSpannedGridPlacement(itemCount = 6, columnCount = 3, minRowCount = 1, spanForIndex = ::uniformSpan)

        assertNoOverlaps(result)
        assertEquals(2, result.totalRowCount)
        assertEquals(listOf(0, 0, 0, 1, 1, 1), result.placements.map { it.row })
        assertEquals(listOf(0, 1, 2, 0, 1, 2), result.placements.map { it.column })
    }

    @Test
    fun `totalRowCount respects minRowCount when content is smaller`() {
        val result = computeSpannedGridPlacement(itemCount = 2, columnCount = 4, minRowCount = 5, spanForIndex = ::uniformSpan)

        assertNoOverlaps(result)
        assertEquals(5, result.totalRowCount)
        assertEquals(5, result.rowToItemIndices.size)
        assertTrue(result.rowToItemIndices[1].isEmpty())
    }

    @Test
    fun `totalRowCount grows past minRowCount when content overflows it`() {
        val result = computeSpannedGridPlacement(itemCount = 12, columnCount = 3, minRowCount = 2, spanForIndex = ::uniformSpan)

        assertNoOverlaps(result)
        assertEquals(4, result.totalRowCount)
    }

    @Test
    fun `a single full-width item occupies the entire first row`() {
        val result =
            computeSpannedGridPlacement(
                itemCount = 1,
                columnCount = 4,
                minRowCount = 1,
                spanForIndex = { SpannedGridItemSpan(columnSpan = 4, rowSpan = 1) },
            )

        assertNoOverlaps(result)
        val placement = result.placements.single()
        assertEquals(0, placement.row)
        assertEquals(0, placement.column)
        assertEquals(4, placement.columnSpan)
    }

    @Test
    fun `a row-spanning item reserves its column in subsequent rows`() {
        // Item 0 spans 2 rows in column 0; items 1..3 should pack around it.
        val spans =
            listOf(
                SpannedGridItemSpan(columnSpan = 1, rowSpan = 2),
                SpannedGridItemSpan(columnSpan = 1, rowSpan = 1),
                SpannedGridItemSpan(columnSpan = 1, rowSpan = 1),
                SpannedGridItemSpan(columnSpan = 1, rowSpan = 1),
            )
        val result =
            computeSpannedGridPlacement(
                itemCount = spans.size,
                columnCount = 2,
                minRowCount = 1,
                spanForIndex = { spans[it] },
            )

        assertNoOverlaps(result)
        // item 0: (row 0, col 0), spans rows 0-1.
        assertEquals(0, result.placements[0].row)
        assertEquals(0, result.placements[0].column)
        // item 1 can't use column 0 on row 0 (taken), so it lands at (0, 1).
        assertEquals(0, result.placements[1].row)
        assertEquals(1, result.placements[1].column)
        // item 2 can't use column 0 on row 1 either (still taken by item 0), so (1, 1).
        assertEquals(1, result.placements[2].row)
        assertEquals(1, result.placements[2].column)
        // item 3 is the first to land on row 2, column 0 — the earliest fully free cell.
        assertEquals(2, result.placements[3].row)
        assertEquals(0, result.placements[3].column)
    }

    @Test
    fun `placement order is dependent on item index order`() {
        val spansA = listOf(SpannedGridItemSpan(2, 1), SpannedGridItemSpan(1, 1))
        val spansB = listOf(SpannedGridItemSpan(1, 1), SpannedGridItemSpan(2, 1))

        val resultA =
            computeSpannedGridPlacement(spansA.size, columnCount = 2, minRowCount = 1) { spansA[it] }
        val resultB =
            computeSpannedGridPlacement(spansB.size, columnCount = 2, minRowCount = 1) { spansB[it] }

        // A: the 2-wide item goes first and claims all of row 0; the 1-wide item is pushed to row 1.
        assertEquals(0, resultA.placements[0].row)
        assertEquals(1, resultA.placements[1].row)

        // B: the 1-wide item goes first at (0, 0); the 2-wide item can't fit next to it, so it's
        // pushed to row 1 even though row 0 has one free cell left.
        assertEquals(0, resultB.placements[0].row)
        assertEquals(1, resultB.placements[1].row)
    }

    @Test
    fun `rowToItemIndices lists every item overlapping a row, including row-spanning ones`() {
        val spans = listOf(SpannedGridItemSpan(columnSpan = 2, rowSpan = 3), SpannedGridItemSpan(1, 1))
        val result =
            computeSpannedGridPlacement(spans.size, columnCount = 3, minRowCount = 1) { spans[it] }

        assertNoOverlaps(result)
        assertEquals(3, result.totalRowCount)
        for (row in 0 until 3) {
            assertTrue(result.rowToItemIndices[row].contains(0))
        }
        assertTrue(result.rowToItemIndices[0].contains(1))
    }

    @Test
    fun `mixed spans matching a typical widget-frame usage pack without overlap`() {
        val spans =
            listOf(
                SpannedGridItemSpan(columnSpan = 2, rowSpan = 2),
                SpannedGridItemSpan(columnSpan = 1, rowSpan = 1),
                SpannedGridItemSpan(columnSpan = 3, rowSpan = 1),
                SpannedGridItemSpan(columnSpan = 1, rowSpan = 2),
                SpannedGridItemSpan(columnSpan = 2, rowSpan = 1),
            )
        val result =
            computeSpannedGridPlacement(spans.size, columnCount = 4, minRowCount = 4) { spans[it] }

        assertNoOverlaps(result)
        assertEquals(5, result.placements.size)
        assertTrue(result.totalRowCount >= 4)
    }
}
