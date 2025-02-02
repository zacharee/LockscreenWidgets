package tk.zwander.common.compose.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import tk.zwander.common.compose.util.rememberBooleanPreferenceState

@Composable
fun PreferenceScreen(
    preferences: List<PreferenceCategory>,
    modifier: Modifier = Modifier,
) {
    val expandedStates = preferences.associate { category ->
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

    LazyColumn(
        modifier = modifier,
    ) {
        preferences.forEach { category ->
            if (category.title != null) {
                item(key = category.key) {
                    PreferenceCategory(
                        category = category,
                        expanded = expandedStates[category.key]?.value ?: true,
                        onExpandChange = { expandedStates[category.key]?.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (expandedStates[category.key]!!.value) {
                items(items = category.items, key = { it.key }) { item ->
                    item.Render(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
