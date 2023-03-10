package tk.zwander.lockscreenwidgets.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.components.TitleBar
import tk.zwander.lockscreenwidgets.R

private data class UsageInfo(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val dialogTitle: Int = title,
    @StringRes val dialogMessage: Int = message,
)

@Composable
private fun rememberUsageInfos(): List<UsageInfo> {
    return remember {
        listOf(
            UsageInfo(
                title = R.string.usage_add_widget,
                message = R.string.usage_add_widget_desc
            ),
            UsageInfo(
                title = R.string.usage_modify_frame,
                message = R.string.usage_modify_frame_desc
            ),
            UsageInfo(
                title = R.string.usage_reorder_widgets,
                message = R.string.usage_reorder_widgets_desc
            ),
            UsageInfo(
                title = R.string.usage_remove_widgets,
                message = R.string.usage_remove_widgets_desc
            ),
            UsageInfo(
                title = R.string.usage_id_blacklists,
                message = R.string.usage_id_blacklists_desc
            ),
            UsageInfo(
                title = R.string.usage_widget_tiles,
                message = R.string.usage_widget_tiles_desc
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageLayout(
    title: String,
    modifier: Modifier = Modifier
) {
    var selectedInfo by remember {
        mutableStateOf<UsageInfo?>(null)
    }

    val items = rememberUsageInfos()

    Surface(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TitleBar(title = title)

            val padding = WindowInsets.Companion.navigationBars.add(
                WindowInsets.Companion.ime
            ).add(
                WindowInsets(16.dp, 16.dp, 16.dp, 16.dp)
            ).asPaddingValues()

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = padding
            ) {
                items(items, { it.hashCode() }) { item ->
                    OutlinedCard(
                        onClick = {
                            selectedInfo = item
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = item.title),
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = stringResource(id = item.message)
                            )
                        }
                    }
                }
            }
        }
    }

    selectedInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { selectedInfo = null },
            title = { Text(text = stringResource(id = info.dialogTitle)) },
            text = { Text(text = stringResource(id = info.dialogMessage)) },
            confirmButton = {
                TextButton(onClick = { selectedInfo = null }) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            }
        )
    }
}
