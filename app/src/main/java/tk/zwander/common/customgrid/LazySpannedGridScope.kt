@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package tk.zwander.common.customgrid

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.layout.LazyLayoutAnimateItemElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/** DSL marker preventing accidental nesting of unrelated lazy-layout scopes. */
@DslMarker annotation class LazySpannedGridScopeMarker

/** Receiver scope used by the item content parameter of [LazyVerticalSpannedGrid]/[LazyHorizontalSpannedGrid]. */
@Stable
sealed interface LazySpannedGridItemScope {
    /**
     * Animates this item's appearance (fade in), disappearance (fade out) and placement changes
     * (such as an item reordering) — mirrors stock `LazyGridItemScope.animateItem` exactly, since
     * it's backed by the same internal [androidx.compose.foundation.lazy.layout.LazyLayoutItemAnimator]
     * (see [measureSpannedGrid]). Unlike a hand-rolled fade, this also animates an item's
     * disappearance when it's genuinely removed from the underlying data, not just when it
     * scrolls out of view.
     *
     * You should also provide a `key` via [LazySpannedGridScope.item]/[LazySpannedGridScope.items]
     * for this modifier to reliably track the item across measure passes.
     *
     * @param fadeInSpec animation spec for the item's appearance. Null disables the fade-in.
     * @param placementSpec animation spec for the item's position changing. Null disables the
     *   placement animation — the item snaps directly to its new position.
     * @param fadeOutSpec animation spec for the item's disappearance. Null disables the fade-out.
     */
    fun Modifier.animateItem(
        fadeInSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
        placementSpec: FiniteAnimationSpec<IntOffset>? =
            spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
        fadeOutSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
    ): Modifier
}

internal object LazySpannedGridItemScopeImpl : LazySpannedGridItemScope {
    override fun Modifier.animateItem(
        fadeInSpec: FiniteAnimationSpec<Float>?,
        placementSpec: FiniteAnimationSpec<IntOffset>?,
        fadeOutSpec: FiniteAnimationSpec<Float>?,
    ): Modifier =
        if (fadeInSpec == null && placementSpec == null && fadeOutSpec == null) {
            this
        } else {
            this then LazyLayoutAnimateItemElement(fadeInSpec, placementSpec, fadeOutSpec)
        }
}

/**
 * Receiver scope used to declare the content of a [LazyVerticalSpannedGrid].
 *
 * Unlike stock `LazyGridScope`, [span] describes a rectangular footprint on both axes
 * ([SpannedGridItemSpan.columnSpan] and [SpannedGridItemSpan.rowSpan]).
 */
@LazySpannedGridScopeMarker
interface LazySpannedGridScope {
    /** Adds a single item. */
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        span: SpannedGridItemSpan = SpannedGridItemSpan(),
        content: @Composable LazySpannedGridItemScope.() -> Unit,
    )

    /** Adds a [count] of items. */
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        span: (index: Int) -> SpannedGridItemSpan = { SpannedGridItemSpan() },
        itemContent: @Composable LazySpannedGridItemScope.(index: Int) -> Unit,
    )
}

/** Adds a [List] of items, one per element. */
inline fun <T> LazySpannedGridScope.items(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    noinline contentType: (item: T) -> Any? = { null },
    noinline span: (item: T) -> SpannedGridItemSpan = { SpannedGridItemSpan() },
    crossinline itemContent: @Composable LazySpannedGridItemScope.(item: T) -> Unit,
) {
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(items[index]) } else null,
        contentType = { index: Int -> contentType(items[index]) },
        span = { index: Int -> span(items[index]) },
    ) { index ->
        itemContent(items[index])
    }
}

/** Adds a [List] of items, one per element, with an index-aware [itemContent]. */
inline fun <T> LazySpannedGridScope.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    noinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    noinline span: (index: Int, item: T) -> SpannedGridItemSpan = { _, _ -> SpannedGridItemSpan() },
    crossinline itemContent: @Composable LazySpannedGridItemScope.(index: Int, item: T) -> Unit,
) {
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(index, items[index]) } else null,
        contentType = { index: Int -> contentType(index, items[index]) },
        span = { index: Int -> span(index, items[index]) },
    ) { index ->
        itemContent(index, items[index])
    }
}
