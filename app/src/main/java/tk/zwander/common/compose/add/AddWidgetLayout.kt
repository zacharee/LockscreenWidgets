package tk.zwander.common.compose.add

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.components.Loader
import tk.zwander.common.compose.settings.ListPickerDialog
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.ListPickerEntry
import tk.zwander.common.data.WidgetListFilters
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWidgetLayout(
    showShortcuts: Boolean,
    showWidgetStackWidget: Boolean,
    fullSize: IntSize,
    gridSize: IntSize,
    onBack: () -> Unit,
    onSelected: (BaseListInfo<*>) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    var filter by remember {
        mutableStateOf<String?>(null)
    }

    var filters by rememberPreferenceState(
        key = PrefManager.KEY_WIDGET_LIST_CURRENT_FILTERS,
        value = { context.prefManager.widgetListFilters },
        onChanged = { _, value ->
            context.prefManager.widgetListFilters = value
        },
    )

    val (items, filteredItems) = items(
        filter = filter,
        showShortcuts = showShortcuts,
        showWidgetStackWidget = showWidgetStackWidget,
        filters = filters,
    )

    var showingFiltersDialog by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Crossfade(
            modifier = Modifier.fillMaxSize(),
            targetState = items.isEmpty(),
            label = "AddWidget",
        ) {
            if (it) {
                Loader(modifier = Modifier.fillMaxSize())
            } else {
                var searchBarHeight by remember {
                    mutableIntStateOf(0)
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AddWidgetScroller(
                        filteredItems = filteredItems,
                        onSelected = onSelected,
                        searchBarHeight = searchBarHeight,
                        modifier = Modifier
                            .fillMaxSize(),
                        fullSize = fullSize,
                        gridSize = gridSize,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp),
                    ) {
                        SearchToolbar(
                            filter = filter,
                            onFilterChanged = { f -> filter = f },
                            onBack = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    searchBarHeight = size.height
                                },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showingFiltersDialog = true },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.filter_list_24px),
                                        contentDescription = stringResource(R.string.filters),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    ListPickerDialog(
        showingDialog = showingFiltersDialog,
        onDialogShowingChanged = { showingFiltersDialog = it },
        state = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = [SheetValue.Hidden, SheetValue.Expanded],
        ),
        entries = remember {
            WidgetListFilters.Category.entries.map { category ->
                ListPickerEntry.WidgetCategoryEntry(
                    label = resources.getString(category.labelRes),
                    value = category,
                )
            }
        },
        currentEntries = filters.currentCategories.map { category ->
            ListPickerEntry.WidgetCategoryEntry(
                label = resources.getString(category.labelRes),
                value = category,
            )
        },
        onEntrySelected = {
            filters = filters.copy(
                currentCategories = if (filters.currentCategories.contains(it.value)) {
                    filters.currentCategories - it.value
                } else {
                    filters.currentCategories + it.value
                },
            )
        },
    )
}
