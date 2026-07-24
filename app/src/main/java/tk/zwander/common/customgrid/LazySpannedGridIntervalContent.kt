package tk.zwander.common.customgrid

import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.runtime.Composable

internal class LazySpannedGridInterval(
    override val key: ((index: Int) -> Any)?,
    val span: (index: Int) -> SpannedGridItemSpan,
    override val type: (index: Int) -> Any?,
    val item: @Composable LazySpannedGridItemScope.(index: Int) -> Unit,
) : LazyLayoutIntervalContent.Interval

internal class LazySpannedGridIntervalContent(content: LazySpannedGridScope.() -> Unit) :
    LazySpannedGridScope, LazyLayoutIntervalContent<LazySpannedGridInterval>() {
    override val intervals = MutableIntervalList<LazySpannedGridInterval>()

    init {
        apply(content)
    }

    override fun item(
        key: Any?,
        contentType: Any?,
        span: SpannedGridItemSpan,
        content: @Composable LazySpannedGridItemScope.() -> Unit,
    ) {
        intervals.addInterval(
            1,
            LazySpannedGridInterval(
                key = key?.let { { key } },
                span = { span },
                type = { contentType },
                item = { content() },
            ),
        )
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        span: (index: Int) -> SpannedGridItemSpan,
        itemContent: @Composable LazySpannedGridItemScope.(index: Int) -> Unit,
    ) {
        intervals.addInterval(
            count,
            LazySpannedGridInterval(
                key = key,
                span = span,
                type = contentType,
                item = itemContent,
            ),
        )
    }

    /** The span of the item at the given global [index]. */
    fun spanOf(index: Int): SpannedGridItemSpan =
        withInterval(index) { localIndex, interval -> interval.span(localIndex) }
}
