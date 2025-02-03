package tk.zwander.common.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import tk.zwander.common.compose.components.TitleBar
import tk.zwander.common.compose.util.rememberBooleanPreferenceState

@Composable
fun PreferenceScreen(
    title: String,
    categories: List<PreferenceCategory>,
    modifier: Modifier = Modifier,
) {
    val expandedStates = categories.associate { category ->
        category.key to if (category.collapsible()) {
            rememberBooleanPreferenceState(
                key = "${category.key}_category_expanded",
            )
        } else {
            remember {
                mutableStateOf(true)
            }
        }
    }

    val filteredCategories = categories.mapNotNull { category ->
        val unfilteredCategoryIsEmpty = category.items.isEmpty()

        val filteredRenderedItems = category.items.filter { it.visible() }
        val filteredCategory = category.copy(
            items = filteredRenderedItems,
        )

        if (filteredRenderedItems.isEmpty() && !unfilteredCategoryIsEmpty) {
            null
        } else {
            filteredCategory to filteredRenderedItems.map { it.key() }
        }
    }

    val insets = WindowInsets.systemBars.add(WindowInsets.ime)

    Surface(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            TitleBar(
                title = title,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = insets.only(WindowInsetsSides.Left + WindowInsetsSides.Right + WindowInsetsSides.Bottom).asPaddingValues(),
            ) {
                filteredCategories.forEachIndexed { index, (category, renderedKeys) ->
                    if (category.title != null) {
                        item(key = category.key) {
                            PreferenceCategory(
                                category = category,
                                expanded = expandedStates[category.key]?.value ?: true,
                                onExpandChange = { expandedStates[category.key]?.value = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                enabled = category.enabled(),
                            )
                        }
                    }

                    if (expandedStates[category.key]?.value != false) {
                        itemsIndexed(items = category.items, key = { itemIndex, _ -> renderedKeys[itemIndex] }) { _, item ->
                            item.Render(
                                modifier = Modifier.fillMaxWidth().animateItem(),
                            )
                        }
                    }

                    if (index < categories.lastIndex) {
                        item(key = "divider_for_category-${category.key}") {
                            HorizontalDivider(modifier = Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}
