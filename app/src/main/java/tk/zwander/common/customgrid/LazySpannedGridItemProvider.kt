package tk.zwander.common.customgrid

import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutKeyIndexMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

internal interface LazySpannedGridItemProvider : LazyLayoutItemProvider {
    /** The [SpannedGridItemSpan] declared for the item at [index]. */
    fun spanOf(index: Int): SpannedGridItemSpan
}

@Composable
internal fun rememberLazySpannedGridItemProviderLambda(
    state: LazySpannedGridState,
    content: LazySpannedGridScope.() -> Unit,
): () -> LazySpannedGridItemProvider {
    val latestContent = rememberUpdatedState(content)
    return remember(state) {
        val intervalContentState =
            derivedStateOf(referentialEqualityPolicy()) {
                LazySpannedGridIntervalContent(latestContent.value)
            }
        val itemProviderState =
            derivedStateOf(referentialEqualityPolicy()) {
                val intervalContent = intervalContentState.value
                val keyIndexMap =
                    LazyLayoutKeyIndexMap(0 until intervalContent.itemCount, intervalContent)
                LazySpannedGridItemProviderImpl(intervalContent, keyIndexMap, state)
            }
        itemProviderState::value
    }
}

private class LazySpannedGridItemProviderImpl(
    private val intervalContent: LazySpannedGridIntervalContent,
    private val keyIndexMap: LazyLayoutKeyIndexMap,
    private val state: LazySpannedGridState,
) : LazySpannedGridItemProvider {
    override val itemCount: Int
        get() = intervalContent.itemCount

    override fun getKey(index: Int): Any =
        keyIndexMap.getKey(index) ?: intervalContent.getKey(index)

    override fun getIndex(key: Any): Int = keyIndexMap.getIndex(key)

    override fun getContentType(index: Int): Any? = intervalContent.getContentType(index)

    override fun spanOf(index: Int): SpannedGridItemSpan = intervalContent.spanOf(index)

    @Composable
    override fun Item(index: Int, key: Any) {
        intervalContent.withInterval(index) { localIndex, content ->
            content.item(LazySpannedGridItemScopeImpl(key, state), localIndex)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LazySpannedGridItemProviderImpl) return false
        return intervalContent == other.intervalContent
    }

    override fun hashCode(): Int = intervalContent.hashCode()
}
