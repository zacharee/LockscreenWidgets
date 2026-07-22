package tk.zwander.common.customgrid

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable
import tk.zwander.common.compose.AppTheme
import kotlin.random.Random

private val previewColors =
    listOf(
        Color(0xFFEF5350),
        Color(0xFF42A5F5),
        Color(0xFF66BB6A),
        Color(0xFFFFA726),
        Color(0xFFAB47BC),
        Color(0xFF26C6DA),
    )

@Composable
private fun PreviewSpannedGridItem(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(previewColors[name.hashCode().let { if (it > 0) it else -it } % previewColors.size]).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name, color = Color.White)
    }
}

/** Uniform 1x1 items, no custom spans — should behave like a plain grid. */
@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun LazySpannedGridUniformPreview() {
    AppTheme {
        LazyVerticalSpannedGrid(
            columnCount = 4,
            rowCount = 4,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            items(16) { index -> PreviewSpannedGridItem(index.toString(), Modifier.fillMaxSize()) }
        }
    }
}

data class SpanItem(
    val title: String,
    val span: SpannedGridItemSpan,
)

/** A mix of 1x1, 2x1, 1x2 and 2x2 spans, packed within the reference 4x4 grid. */
@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun LazySpannedGridMixedSpansPreview() {
    var spans by remember {
        mutableStateOf(
            List(15) {
                SpanItem(
                    title = "Item #$it",
                    span = SpannedGridItemSpan(
                        columnSpan = Random.nextInt(1, 3),
                        rowSpan = Random.nextInt(1, 3),
                    ),
                )
            }
        )
    }
    AppTheme {
        val reorderableState = rememberReorderableLazySpannedGridState(
            onMove = { from, to ->
                spans = spans.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            },
        )

        LazyVerticalSpannedGrid(
            state = reorderableState.gridState,
            columnCount = 6,
            rowCount = 4,
            modifier = Modifier.fillMaxSize()
                .reorderable(reorderableState)
                .detectReorderAfterLongPress(reorderableState),
        ) {
            items(spans.size, span = { spans[it].span }, key = { spans[it].title }) { index ->
                ReorderableItem(reorderableState, key = spans[index].title, orientationLocked = false) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp)
                    val scale by animateFloatAsState(if (isDragging) 1.08f else 1f)
                    PreviewSpannedGridItem(
                        spans[index].title,
                        Modifier.fillMaxSize()
                            .scale(scale)
                            .shadow(elevation)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

/** More content than the reference row count — verifies the grid scrolls past it. */
@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun LazySpannedGridOverflowPreview() {
    AppTheme {
        LazyVerticalSpannedGrid(
            columnCount = 4,
            rowCount = 4,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            items(40) { index -> PreviewSpannedGridItem(index.toString(), Modifier.fillMaxSize()) }
        }
    }
}

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun LazySpannedGridSmallPreview() {
    AppTheme {
        LazyVerticalSpannedGrid(
            columnCount = 3,
            rowCount = 3,
            modifier = Modifier.height(200.dp).background(MaterialTheme.colorScheme.surface),
        ) {
            item(span = SpannedGridItemSpan(columnSpan = 2, rowSpan = 2)) {
                PreviewSpannedGridItem("0", Modifier.fillMaxSize())
            }
            items(5) { index -> PreviewSpannedGridItem(index.toString(), Modifier.fillMaxSize()) }
        }
    }
}

/** Horizontal counterpart of [LazySpannedGridUniformPreview] — uniform 1x1 items. */
@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun LazySpannedGridHorizontalUniformPreview() {
    AppTheme {
        LazyHorizontalSpannedGrid(
            rowCount = 4,
            columnCount = 4,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            items(16) { index -> PreviewSpannedGridItem(index.toString(), Modifier.fillMaxSize()) }
        }
    }
}

/** Horizontal counterpart of [LazySpannedGridMixedSpansPreview] — mixed column/row spans. */
@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun LazySpannedGridHorizontalMixedSpansPreview() {
    val spans =
        listOf(
            SpannedGridItemSpan(columnSpan = 2, rowSpan = 5),
            SpannedGridItemSpan(columnSpan = 1, rowSpan = 1),
            SpannedGridItemSpan(columnSpan = 1, rowSpan = 2),
            SpannedGridItemSpan(columnSpan = 2, rowSpan = 4),
            SpannedGridItemSpan(columnSpan = 1, rowSpan = 1),
            SpannedGridItemSpan(columnSpan = 1, rowSpan = 3),
            SpannedGridItemSpan(columnSpan = 2, rowSpan = 1),
            SpannedGridItemSpan(columnSpan = 1, rowSpan = 1),
            SpannedGridItemSpan(columnSpan = 2, rowSpan = 2),
            SpannedGridItemSpan(columnSpan = 2, rowSpan = 2),
            SpannedGridItemSpan(columnSpan = 2, rowSpan = 1),
        )
    AppTheme {
        LazyHorizontalSpannedGrid(
            rowCount = 5,
            columnCount = 4,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            items(spans.size, span = { spans[it] }) { index ->
                PreviewSpannedGridItem(index.toString(), Modifier.fillMaxSize())
            }
        }
    }
}

/** More content than the reference column count — verifies the grid scrolls past it. */
@Preview(widthDp = 320, heightDp = 320)
@Composable
private fun LazySpannedGridHorizontalOverflowPreview() {
    AppTheme {
        LazyHorizontalSpannedGrid(
            rowCount = 4,
            columnCount = 4,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            items(40) { index -> PreviewSpannedGridItem(index.toString(), Modifier.fillMaxSize()) }
        }
    }
}

/**
 * Exercises `animateItem()`: Shuffle re-flows items to new grid cells (placement animation);
 * Add/Remove exercise appearance (fade-in) and, for Remove specifically, the scrolled-off-screen
 * fade-out — removing the last item is instant since it's a genuine data removal (see the
 * limitation documented on [LazySpannedGridItemScope.animateItem]). Run in Android Studio's
 * Interactive Preview to see the animations.
 */
@Preview(widthDp = 320, heightDp = 380)
@Composable
private fun LazySpannedGridAnimateItemPreview() {
    var values by remember { mutableStateOf((0 until 9).toList()) }
    var nextValue by remember { mutableStateOf(9) }

    AppTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row {
                Button(onClick = { values = values.shuffled() }) { Text("Shuffle") }
                Button(onClick = { values = values + nextValue.also { nextValue++ } }) { Text("Add") }
                Button(onClick = { values = values.dropLast(1) }) { Text("Remove") }
            }
            LazyVerticalSpannedGrid(
                columnCount = 3,
                rowCount = 3,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            ) {
                items(values, key = { it }) { value ->
                    PreviewSpannedGridItem(value.toString(), Modifier.fillMaxSize().animateItem())
                }
            }
        }
    }
}
