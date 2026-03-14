package tk.zwander.common.compose.settings

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.components.AnimatedBottomSheet
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.ListPickerEntry
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R

typealias ListPreferenceEntry = ListPickerEntry.StringEntry

class ListPreference(
    title: @Composable () -> String,
    summary: @Composable () -> String?,
    val entries: @Composable () -> List<ListPreferenceEntry>,
    key: @Composable () -> String,
    defaultValue: @Composable () -> String?,
    icon: @Composable () -> Painter? = { null },
    enabled: @Composable () -> Boolean = { true },
    visible: @Composable () -> Boolean = { true },
) : BasePreference<String?>(
    title = title,
    summary = summary,
    key = key,
    defaultValue = defaultValue,
    icon = icon,
    enabled = enabled,
    visible = visible,
) {
    @Composable
    override fun Render(modifier: Modifier) {
        ListPreference(
            title = title(),
            summary = summary(),
            entries = entries(),
            key = key(),
            defaultValue = defaultValue(),
            modifier = modifier,
            icon = icon(),
            enabled = enabled(),
        )
    }
}

@Composable
fun ListPreference(
    title: String,
    summary: String?,
    entries: List<ListPreferenceEntry>,
    key: String,
    defaultValue: String?,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var value by rememberPreferenceState(
        key = key,
        value = { context.prefManager.getString(it, defaultValue) },
        onChanged = { k, v -> context.prefManager.putString(k, v) },
    )

    ListPreference(
        title = title,
        summary = summary,
        entries = entries,
        currentValue = value,
        onValueSelected = { value = it },
        modifier = modifier,
        icon = icon,
        enabled = enabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPreference(
    title: String,
    summary: String?,
    entries: List<ListPreferenceEntry>,
    currentValue: String?,
    onValueSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    var showingDialog by remember {
        mutableStateOf(false)
    }
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    BasePreferenceLayout(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        widgetPosition = WidgetPosition.BOTTOM_INLINE,
        widget = entries.find { it.value == currentValue }?.label?.let {
            { modifier: Modifier ->
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = modifier,
                )
            }
        },
        onClick = {
            showingDialog = true
        },
    )

    ListPickerDialog(
        showingDialog = showingDialog,
        onDialogShowingChanged = { showingDialog = it },
        entries = entries,
        currentEntries = entries.filter { it.value == currentValue },
        onEntrySelected = { onValueSelected(it.value) },
        state = state,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <V, T : ListPickerEntry<V>> ListPickerDialog(
    showingDialog: Boolean,
    onDialogShowingChanged: (Boolean) -> Unit,
    entries: List<T>,
    currentEntries: List<T>,
    onEntrySelected: (T) -> Unit,
    state: SheetState = rememberModalBottomSheetState(),
) {
    AnimatedBottomSheet(
        onDismissRequest = { onDialogShowingChanged(false) },
        sheetState = state,
        isVisible = showingDialog,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(items = entries, key = { it.getKey() }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { onEntrySelected(entry) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.label,
                        modifier = Modifier.weight(1f),
                    )

                    Box(
                        modifier = Modifier.width(48.dp).heightIn(min = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = currentEntries.contains(entry),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_check_24),
                                contentDescription = stringResource(R.string.selected),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(
                onClick = { onDialogShowingChanged(false) },
            ) {
                Text(
                    text = stringResource(R.string.done)
                )
            }
        }
    }
}
