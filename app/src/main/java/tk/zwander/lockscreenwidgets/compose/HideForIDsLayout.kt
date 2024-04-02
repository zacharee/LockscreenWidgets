package tk.zwander.lockscreenwidgets.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.components.TitleBar
import tk.zwander.lockscreenwidgets.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HideForIDsLayout(
    items: Set<String>,
    title: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBackUpClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdding by remember(items) {
        mutableStateOf(false)
    }
    var idToAdd by remember(items, isAdding) {
        mutableStateOf("")
    }

    Surface(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TitleBar(title = title)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                items(items.toList(), { it }) { id ->
                    val state = rememberSwipeToDismissBoxState()

                    LaunchedEffect(key1 = state.currentValue, key2 = state.targetValue) {
                        if (state.currentValue != SwipeToDismissBoxValue.Settled &&
                            state.targetValue != SwipeToDismissBoxValue.Settled) {
                            onRemove(id)
                        }
                    }

                    SwipeToDismissBox(
                        state = state,
                        backgroundContent = {
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
                        modifier = Modifier.animateItemPlacement(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(text = id)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .animateContentSize(),
                contentAlignment = Alignment.Center
            ) {
                // Messy workaround to keep the height consistent.
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.alpha(0f),
                    label = { Text(text = "") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBackUpClicked,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_save_24),
                            contentDescription = stringResource(id = R.string.back_up)
                        )
                    }

                    IconButton(
                        onClick = { isAdding = true },
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_add_24),
                            contentDescription = stringResource(id = R.string.add_id)
                        )
                    }

                    IconButton(
                        onClick = onRestoreClicked,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_restore_24),
                            contentDescription = stringResource(id = R.string.restore)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isAdding,
                    modifier = Modifier.fillMaxWidth(),
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.CenterHorizontally),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedTextField(
                            value = idToAdd,
                            onValueChange = { newValue -> idToAdd = newValue },
                            placeholder = {
                                Text(
                                    text = stringResource(id = R.string.add_id_hint)
                                )
                            },
                            leadingIcon = {
                                IconButton(onClick = { isAdding = false }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.baseline_clear_24),
                                        contentDescription = stringResource(id = android.R.string.cancel)
                                    )
                                }
                            },
                            trailingIcon = if (idToAdd.isNotBlank()) {
                                {
                                    IconButton(onClick = { onAdd(idToAdd) }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.baseline_check_24),
                                            contentDescription = stringResource(id = R.string.add)
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
