package tk.zwander.common.customgrid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

/** DSL marker preventing accidental nesting of unrelated lazy-layout scopes. */
@DslMarker annotation class LazySpannedGridScopeMarker

/** Receiver scope used by the item content parameter of [LazyVerticalSpannedGrid]/[LazyHorizontalSpannedGrid]. */
@Stable
sealed interface LazySpannedGridItemScope {
    /**
     * Animates this item's appearance (fade in), and its placement changes (e.g. from other items
     * being added, removed, resized, or reordered) — mirroring stock `LazyGridItemScope.animateItem`.
     *
     * You should also provide a `key` via [LazySpannedGridScope.item]/[LazySpannedGridScope.items]
     * for this modifier to reliably track the item across measure passes.
     *
     * **Fade-out limitation:** [fadeOutSpec] can only animate an item scrolling outside the
     * visible viewport while it's still present in the grid's content — the grid keeps composing
     * it briefly at its (still-valid) position while its alpha fades out. If the item is actually
     * removed from the underlying data, it disappears immediately with no animation: Compose's own
     * `animateItem` support for that case relies on `androidx.compose.foundation` internals with no
     * public equivalent (it keeps the item's existing composition alive by key after its index is
     * gone, which isn't achievable from outside the module). This mirrors the same limitation the
     * pre-1.7 `Modifier.animateItemPlacement()` had.
     *
     * @param fadeInSpec animation spec for the item's appearance. Null disables the fade-in.
     * @param placementSpec animation spec for the item's position changing. Null disables the
     *   placement animation — the item snaps directly to its new position.
     * @param fadeOutSpec animation spec for the item scrolling out of view (see the limitation
     *   above). Null disables the fade-out.
     */
    fun Modifier.animateItem(
        fadeInSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
        placementSpec: FiniteAnimationSpec<IntOffset>? =
            spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
        fadeOutSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
    ): Modifier
}

internal class LazySpannedGridItemScopeImpl(
    private val key: Any,
    private val state: LazySpannedGridState,
) : LazySpannedGridItemScope {
    override fun Modifier.animateItem(
        fadeInSpec: FiniteAnimationSpec<Float>?,
        placementSpec: FiniteAnimationSpec<IntOffset>?,
        fadeOutSpec: FiniteAnimationSpec<Float>?,
    ): Modifier =
        composed {
            // Registered so measureSpannedGrid knows, once this item later disappears, whether
            // (and how) to animate its placement/fade-out — decisions only the outer grid can make.
            DisposableEffect(key, placementSpec, fadeOutSpec) {
                state.itemAnimationSpecs[key] = ItemAnimationSpecs(placementSpec, fadeOutSpec)
                onDispose { state.itemAnimationSpecs.remove(key) }
            }

            if (fadeInSpec == null) {
                this@animateItem
            } else {
                val alpha = remember(key) { Animatable(0f) }
                LaunchedEffect(key) { alpha.animateTo(1f, fadeInSpec) }
                this@animateItem.graphicsLayer { this.alpha = alpha.value }
            }
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
