package tk.zwander.lockscreenwidgets.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.lockscreenwidgets.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HideForIDsLayout(
    items: Set<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.toList(), { it }) { id ->
                val state = rememberDismissState()
                
                if (state.currentValue != DismissValue.Default) {
                    onRemove(id)
                }
                
                SwipeToDismiss(
                    state = state,
                    background = {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .fillMaxHeight()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                                contentDescription = stringResource(id = R.string.remove),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Icon(
                                painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                                contentDescription = stringResource(id = R.string.remove),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }, 
                    dismissContent = {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(text = id)
                        }
                    },
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }
    }
}
