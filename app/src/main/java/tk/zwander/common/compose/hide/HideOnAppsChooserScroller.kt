package tk.zwander.common.compose.hide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.common.compose.components.CardSwitch
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.BasicAppInfo
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager

@Composable
fun HideOnAppsChooserScroller(
    filteredItems: List<BasicAppInfo>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var checked by context.rememberPreferenceState(
        key = PrefManager.KEY_HIDE_FRAME_ON_APPS,
        value = { context.prefManager.hideFrameOnApps.toMutableSet() },
        onChanged = {
            context.prefManager.hideFrameOnApps = it
        }
    )

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(filteredItems, key = { it.appInfo.packageName }) {
            CardSwitch(
                enabled = checked.contains(it.appInfo.packageName),
                onEnabledChanged = { enabled ->
                   checked = checked.toMutableSet().apply {
                       if (enabled) {
                           add(it.appInfo.packageName)
                       } else {
                           remove(it.appInfo.packageName)
                       }
                   }
                },
                title = it.appName,
                summary = it.appInfo.packageName,
                icon = rememberDrawablePainter(
                    drawable = it.appInfo.loadIcon(context.packageManager)
                ),
                titleTextStyle = MaterialTheme.typography.titleMedium,
                summaryTextStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}
