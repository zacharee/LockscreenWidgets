package tk.zwander.common.compose.add

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
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.components.Loader
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo

@Composable
fun AddWidgetLayout(
    showShortcuts: Boolean,
    onBack: () -> Unit,
    onSelected: (BaseListInfo<*>) -> Unit,
) {
    var filter by remember {
        mutableStateOf<String?>(null)
    }

    val (items, filteredItems) = items(
        filter = filter,
        showShortcuts = showShortcuts,
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
                label = "AddWidget"
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

                        AddWidgetScroller(
                            filteredItems = filteredItems,
                            onSelected = onSelected,
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
