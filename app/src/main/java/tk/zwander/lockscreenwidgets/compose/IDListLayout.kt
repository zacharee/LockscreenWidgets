package tk.zwander.lockscreenwidgets.compose

import android.content.ClipData
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tk.zwander.common.util.DebugIDsManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.IDData

@Composable
fun IDListLayout(
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val displayItems by DebugIDsManager.items.collectAsState()

    Surface(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
        ) {
            stickyHeader(key = "copy") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(id = R.string.id_list))

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            "ids",
                                            displayItems.joinToString("\n") { it.id },
                                        ),
                                    ),
                                )
                            }
                        },
                    ) {
                        Text(text = stringResource(id = R.string.copy))
                    }
                }
            }

            items(items = displayItems, key = { it.id }) { id ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = when (id.type) {
                                    IDData.IDType.ADDED -> Color.Green
                                    IDData.IDType.REMOVED -> Color.Red
                                    IDData.IDType.SAME -> MaterialTheme.colorScheme.onSurface
                                },
                            ),
                        ) {
                            append(id.id)
                        }
                    },
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .animateItem(),
                    lineHeight = 12.sp,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
