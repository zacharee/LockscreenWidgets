package tk.zwander.common.compose.hide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.common.compose.components.CardSwitch
import tk.zwander.common.compose.util.insetsContentPadding
import tk.zwander.common.data.BasicAppInfo

@Composable
fun HideOnAppsChooserScroller(
    filteredItems: Collection<BasicAppInfo>,
    checked: Set<String>,
    onCheckedChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = insetsContentPadding(
            WindowInsets.navigationBars,
            WindowInsets.ime,
            extraPadding = PaddingValues(8.dp)
        )
    ) {
        items(filteredItems.toList(), key = { it.appInfo.packageName }) {
            CardSwitch(
                enabled = it.isChecked,
                onEnabledChanged = { enabled ->
                    onCheckedChanged(
                        checked.run {
                            if (enabled) {
                                plus(it.appInfo.packageName)
                            } else {
                                minus(it.appInfo.packageName)
                            }
                        }
                    )
                },
                title = it.appName,
                summary = it.appInfo.packageName,
                icon = rememberDrawablePainter(
                    drawable = it.appInfo.loadIcon(context.packageManager),
                ),
                titleTextStyle = MaterialTheme.typography.titleMedium,
                summaryTextStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.animateItem(),
                backgroundColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}
