package tk.zwander.common.compose.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import tk.zwander.common.compose.util.rememberBooleanPreferenceState

@Composable
fun PreferenceScreen(
    categories: List<PreferenceCategory>,
    modifier: Modifier = Modifier,
) {
    val expandedStates = categories.associate { category ->
        category.key to if (category.collapsible) {
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

        val filteredCategory = category.copy(
            items = category.items.filter { it.visible() },
        )

        if (filteredCategory.items.isEmpty() && !unfilteredCategoryIsEmpty) {
            null
        } else {
            filteredCategory
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = (WindowInsets.systemBars.add(WindowInsets.ime)).asPaddingValues(),
    ) {
        filteredCategories.forEachIndexed { index, category ->
            if (category.title != null) {
                item(key = category.key) {
                    PreferenceCategory(
                        category = category,
                        expanded = expandedStates[category.key]?.value ?: true,
                        onExpandChange = { expandedStates[category.key]?.value = it },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        enabled = category.enabled(),
                    )
                }
            }

            if (expandedStates[category.key]?.value == true) {
                items(items = category.items, key = { it.key }) { item ->
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
