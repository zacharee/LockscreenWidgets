package tk.zwander.common.compose.hide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.data.BasicAppInfo
import tk.zwander.common.util.getInstalledApplicationsCompat

@Composable
internal fun items(
    filter: String?
): Pair<List<BasicAppInfo>, List<BasicAppInfo>> {
    val context = LocalContext.current

    val items = remember {
        mutableStateListOf<BasicAppInfo>()
    }

    LaunchedEffect(null) {
        val apps = withContext(Dispatchers.IO) {
            context.packageManager.getInstalledApplicationsCompat().map {
                BasicAppInfo(
                    appName = it.loadLabel(context.packageManager).toString(),
                    appInfo = it
                )
            }
        }

        items.clear()
        items.addAll(apps)
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
