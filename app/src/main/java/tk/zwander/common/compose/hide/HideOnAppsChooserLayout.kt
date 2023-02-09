package tk.zwander.common.compose.hide

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.add.SearchToolbar
import tk.zwander.common.compose.components.Loader

@Composable
fun HideOnAppsChooserLayout(
    onBack: () -> Unit,
) {
    var filter by remember {
        mutableStateOf<String?>(null)
    }

    val (items, filteredItems) = items(
        filter = filter
    )

    AppTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .systemBarsPadding()
        ) {
            Crossfade(
                modifier = Modifier.fillMaxSize(),
                targetState = items.isEmpty()
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
                            modifier = Modifier.fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}