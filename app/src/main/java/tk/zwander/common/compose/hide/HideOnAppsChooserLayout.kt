package tk.zwander.common.compose.hide

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.add.SearchToolbar
import tk.zwander.common.compose.components.Loader
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager

@Composable
fun HideOnAppsChooserLayout(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var filter by remember {
        mutableStateOf<String?>(null)
    }

    var checked by context.rememberPreferenceState<Set<String>>(
        key = PrefManager.KEY_HIDE_FRAME_ON_APPS,
        value = { context.prefManager.hideFrameOnApps.toMutableSet() },
        onChanged = {
            context.prefManager.hideFrameOnApps = it
        }
    )

    val (items, filteredItems) = items(
        filter = filter,
        checked = checked
    )

    AppTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Crossfade(
                modifier = Modifier.fillMaxSize(),
                targetState = items.isEmpty(),
                label = "HideChooser"
            ) {
                if (it) {
                    Loader(modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SearchToolbar(
                            filter = filter,
                            onFilterChanged = { f -> filter = f },
                            onBack = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )

                        HideOnAppsChooserScroller(
                            filteredItems = filteredItems,
                            checked = checked,
                            onCheckedChanged = { newChecked -> checked = newChecked },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}
