package tk.zwander.lockscreenwidgets.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.compose.AppTheme
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.IDData
import java.util.TreeSet

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IDListLayout(
    items: Set<String>,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    var displayItems by remember {
        mutableStateOf<Set<IDData>>(setOf())
    }

    LaunchedEffect(items.toList()) {
        withContext(Dispatchers.IO) {
            val oldIdsFull = displayItems.map { it.id }
            val oldIdsWithoutRemoved = displayItems
                .filter { it.type != IDData.IDType.REMOVED }
                .map { it.id }
            if (!oldIdsWithoutRemoved.containsAll(items) || !items.containsAll(oldIdsWithoutRemoved)) {
                val newItems = TreeSet<IDData>()
                newItems.addAll(
                    (items + oldIdsFull).map { id ->
                        IDData(
                            id = id,
                            type = when {
                                !items.contains(id) -> IDData.IDType.REMOVED
                                !oldIdsWithoutRemoved.contains(id) -> IDData.IDType.ADDED
                                else -> IDData.IDType.SAME
                            }
                        )
                    }
                )

                displayItems = newItems
            }
        }
    }

    AppTheme {
        Surface(modifier = modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                stickyHeader(key = "copy") {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(id = R.string.id_list))

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                clipboard.setText(buildAnnotatedString {
                                    append(displayItems.joinToString("\n") { it.id })
                                })
                            }
                        ) {
                            Text(text = stringResource(id = R.string.copy))
                        }
                    }
                }

                items(displayItems.toList(), key = { it.id }) { id ->
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = when (id.type) {
                                        IDData.IDType.ADDED -> Color.Green
                                        IDData.IDType.REMOVED -> Color.Red
                                        IDData.IDType.SAME -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            ) {
                                append(id.id)
                            }
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                            .animateItemPlacement(),
                        lineHeight = 12.sp,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
