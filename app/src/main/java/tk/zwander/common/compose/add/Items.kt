package tk.zwander.common.compose.add

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.compose.util.matchesFilter
import tk.zwander.common.data.AppInfo
import tk.zwander.common.util.getApplicationInfoCompat
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.queryIntentActivitiesCompat
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo

@Composable
internal fun items(
    filter: String?,
    appWidgetManager: AppWidgetManager,
    showShortcuts: Boolean
): Pair<List<AppInfo>, List<AppInfo>> {
    val context = LocalContext.current

    val items = remember {
        mutableStateListOf<AppInfo>()
    }

    LaunchedEffect(null) {
        val apps = withContext(Dispatchers.IO) {
            val apps = HashMap<String, AppInfo>()
            val packageManager = context.packageManager

            (appWidgetManager.installedProviders +
                    appWidgetManager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) +
                    appWidgetManager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX)
                    ).forEach {
                    try {
                        val appInfo =
                            packageManager.getApplicationInfoCompat(it.provider.packageName, 0)

                        val appName = packageManager.getApplicationLabel(appInfo)
                        val widgetName = it.loadLabel(packageManager)

                        var app = apps[appInfo.packageName]
                        if (app == null) {
                            app = AppInfo(appName.toString(), appInfo)
                            apps[appInfo.packageName] = app
                        }

                        if (appInfo.packageName.contains("overdrop")) {
                            context.logUtils.normalLog("Adding overdrop info $it")
                        }

                        app.widgets.add(
                            WidgetListInfo(
                                widgetName,
                                it.previewImage.run { if (this != 0) this else appInfo.icon },
                                appInfo,
                                it,
                            )
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        context.logUtils.debugLog("Unable to parse application info for widget", e)
                    }
                }

            if (showShortcuts) {
                packageManager.queryIntentActivitiesCompat(
                    Intent(Intent.ACTION_CREATE_SHORTCUT),
                    PackageManager.GET_RESOLVED_FILTER
                ).forEach {
                    try {
                        val appInfo =
                            packageManager.getApplicationInfoCompat(it.activityInfo.packageName)

                        val appName = appInfo.loadLabel(packageManager)
                        val shortcutName = it.loadLabel(packageManager)

                        val app = apps[appInfo.packageName] ?: AppInfo(appName.toString(), appInfo).apply {
                            apps[appInfo.packageName] = this
                        }

                        app.shortcuts.add(
                            ShortcutListInfo(
                                shortcutName.toString(),
                                it.iconResource,
                                appInfo,
                                it,
                            )
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        context.logUtils.debugLog(
                            "Unable to parse application info for shortcut",
                            e
                        )
                    }
                }
            }

            apps
        }

        items.clear()
        items.addAll(apps.values.sorted())
    }

    val filteredItems by remember(filter) {
        derivedStateOf {
            items.filter { it.matchesFilter(filter) }
        }
    }

    return items to filteredItems
}
