package tk.zwander.common.compose.hide

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.data.BasicAppInfo
import tk.zwander.common.util.getInstalledApplicationsCompat

@Composable
internal fun items(
    filter: String?,
    checked: Set<String>,
): Pair<Collection<BasicAppInfo>, Collection<BasicAppInfo>> {
    val context = LocalContext.current

    var items by remember {
        mutableStateOf<Set<BasicAppInfo>>([])
    }

    LaunchedEffect(key1 = checked) {
        items = items.map {
            it.copy(
                isChecked = checked.contains(it.appInfo.packageName)
            )
        }.toSortedSet()
    }

    LaunchedEffect(null) {
        val apps = withContext(Dispatchers.IO) {
            context.packageManager.getInstalledApplicationsCompat().map {
                BasicAppInfo(
                    appName = it.loadLabel(context.packageManager).toString(),
                    appInfo = it,
                    isChecked = checked.contains(it.packageName)
                )
            }
        }

        items = apps.toSortedSet()
    }

    val filteredItems by remember(filter) {
        derivedStateOf {
            if (filter.isNullOrBlank()) {
                items
            } else {
                items.filter { app ->
                    app.appName.contains(filter, true) ||
                            app.appInfo.packageName.contains(filter, true)
                }
            }
        }
    }

    return items to filteredItems
}
