package tk.zwander.common.customgrid

import androidx.compose.ui.unit.IntSize

/**
 * The footprint of an item in a [LazyVerticalSpannedGrid], in grid cells.
 *
 * Unlike stock Compose's `GridItemSpan`, this spans both axes: an item can occupy multiple rows
 * as well as multiple columns, mirroring `com.arasthel.spannedgridlayoutmanager.SpanSize`.
 */
data class SpannedGridItemSpan(
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
) {
    constructor(intSize: IntSize) : this(intSize.width, intSize.height)
}
