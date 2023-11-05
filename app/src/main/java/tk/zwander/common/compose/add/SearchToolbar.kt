package tk.zwander.common.compose.add

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import tk.zwander.lockscreenwidgets.R

@Composable
fun SearchToolbar(
    filter: String?,
    onFilterChanged: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val focusManager = LocalFocusManager.current

        val state = remember {
            MutableInteractionSource()
        }
        val isFocused by state.collectIsFocusedAsState()

        OutlinedTextField(
            value = filter ?: "",
            onValueChange = onFilterChanged,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            label = { Text(text = stringResource(id = R.string.search)) },
            trailingIcon = if (!filter.isNullOrBlank() || isFocused) {
                {
                    IconButton(
                        onClick = {
                            if (filter.isNullOrBlank()) {
                                focusManager.clearFocus(true)
                            } else {
                                onFilterChanged(null)
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_clear_24),
                            contentDescription = stringResource(id = R.string.clear)
                        )
                    }
                }
            } else null,
            leadingIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_arrow_back_24),
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
            },
            interactionSource = state,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
            ),
        )
    }
}