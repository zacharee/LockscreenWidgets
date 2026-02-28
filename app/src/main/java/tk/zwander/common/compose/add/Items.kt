package tk.zwander.common.compose.add

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.compose.util.matchesFilter
import tk.zwander.common.data.AppInfo
import tk.zwander.common.iconpacks.iconPackManager
import tk.zwander.common.util.BrokenAppsRegistry
import tk.zwander.common.util.componentInfoCompat
import tk.zwander.common.util.componentNameCompat
import tk.zwander.common.util.density
import tk.zwander.common.util.getAllInstalledWidgetProviders
import tk.zwander.common.util.getApplicationInfoCompat
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.queryIntentActivitiesCompat
import tk.zwander.common.util.toSafeBitmap
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider
import tk.zwander.lockscreenwidgets.data.list.LauncherItemListInfo
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo
import java.util.TreeSet

@SuppressLint("RestrictedApi")
@Composable
internal fun items(
    filter: String?,
    showShortcuts: Boolean,
    showWidgetStackWidget: Boolean,
): Pair<List<AppInfo>, List<AppInfo>> {
    val context = LocalContext.current

    val items = remember {
        mutableStateListOf<AppInfo>()
    }

    LaunchedEffect(null) {
        val apps = withContext(Dispatchers.IO) {
            val apps = HashMap<String, AppInfo>()
            val packageManager = context.packageManager

            context.getAllInstalledWidgetProviders().forEach {
                if (BrokenAppsRegistry.isBroken(it)) {
                    context.logUtils.debugLog("Hiding broken widget ${it.provider}.")
                    return@forEach
                }

                if (!showWidgetStackWidget && it.provider == ComponentName(context, WidgetStackProvider::class.java)) {
                    context.logUtils.debugLog("Excluding widget stack widget.")
                    return@forEach
                }

                try {
                    val appInfo = packageManager
                        .getApplicationInfoCompat(it.provider.packageName, 0)
                    val appResources = packageManager.getResourcesForApplication(appInfo)

                    val appName = packageManager.getApplicationLabel(appInfo)
                    val widgetName = it.loadLabel(packageManager)

                    var app = apps[appInfo.packageName]
                    if (app == null) {
                        app = AppInfo(appName.toString(), appInfo)
                        apps[appInfo.packageName] = app
                    }

                    app.widgets.add(
                        WidgetListInfo(
                            widgetName,
                            it.previewImage.run { if (this != 0) this else appInfo.icon }
                                .let { iconResource ->
                                    try {
                                        IconCompat.createWithResource(
                                            appResources,
                                            appInfo.packageName,
                                            iconResource,
                                        )
                                    } catch (e: IllegalArgumentException) {
                                        context.logUtils.debugLog("Error creating icon", e)
                                        null
                                    }
                                },
                            app,
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
                        val appResources = packageManager.getResourcesForApplication(appInfo)

                        val appName = appInfo.loadLabel(packageManager)
                        val shortcutName = it.loadLabel(packageManager)

                        val app = apps[appInfo.packageName] ?: AppInfo(
                            appName.toString(),
                            appInfo
                        ).apply {
                            apps[appInfo.packageName] = this
                        }

                        app.shortcuts.add(
                            ShortcutListInfo(
                                shortcutName.toString(),
                                it.iconResource.run { if (this != 0) this else appInfo.icon }
                                    .let { iconResource ->
                                        try {
                                            IconCompat.createWithResource(
                                                appResources,
                                                appInfo.packageName,
                                                iconResource
                                            )
                                        } catch (e: IllegalArgumentException) {
                                            context.logUtils.debugLog("Error creating icon", e)
                                            null
                                        }
                                    },
                                app,
                                it,
                            )
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        context.logUtils.debugLog(
                            "Unable to parse application info for shortcut",
                            e,
                        )
                    }
                }

                packageManager.queryIntentActivitiesCompat(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    },
                    0,
                ).forEach { launcherItem ->
                    try {
                        val appInfo =
                            packageManager.getApplicationInfoCompat(launcherItem.activityInfo.packageName)
                        val appResources = packageManager.getResourcesForApplication(appInfo)
                        val appName = appInfo.loadLabel(packageManager)

                        val appEntry = apps.getOrPut(appInfo.packageName) {
                            AppInfo(appName.toString(), appInfo)
                        }

                        appEntry.launcherItems.add(
                            LauncherItemListInfo(
                                appName = appName.toString(),
                                icon = context.iconPackManager.currentIconPack.value
                                    ?.resolveIcon(
                                        context,
                                        launcherItem.componentInfoCompat.componentNameCompat,
                                    )
                                    ?.toSafeBitmap(context.density, maxSize = 128.dp)
                                    ?.let { IconCompat.createWithBitmap(it) } ?: (
                                        launcherItem.iconResource.run { if (this != 0) this else appInfo.icon }
                                            .let { iconResource ->
                                                try {
                                                    IconCompat.createWithResource(
                                                        appResources,
                                                        appInfo.packageName,
                                                        iconResource
                                                    )
                                                } catch (e: IllegalArgumentException) {
                                                    context.logUtils.debugLog("Error creating icon", e)
                                                    null
                                                }
                                            }
                                        ),
                                appInfo = appEntry,
                                itemInfo = launcherItem,
                            ),
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        context.logUtils.debugLog(
                            "Unable to parse application info for launcher",
                            e,
                        )
                    }
                }

//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
//                    val iShortcutManager = IShortcutService.Stub.asInterface(ServiceManager.getService(Context.SHORTCUT_SERVICE))
//
//                    packageManager.getInstalledApplicationsCompat().forEach { appInfo ->
//                        val appName = appInfo.loadLabel(packageManager)
//
//                        @Suppress("UNCHECKED_CAST")
//                        val shortcuts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                            iShortcutManager.getShortcuts(
//                                appInfo.packageName,
//                                ShortcutManager.FLAG_MATCH_MANIFEST or ShortcutManager.FLAG_MATCH_DYNAMIC,
//                                context.userId,
//                            ).list as List<ShortcutInfo>
//                        } else {
//                            val manifest = iShortcutManager::class.java
//                                .getMethod("getManifestShortcuts", String::class.java, Int::class.java)
//                                .invoke(iShortcutManager, appInfo.packageName, context.userId) as ParceledListSlice<ShortcutInfo>
//                            val dynamic = iShortcutManager::class.java
//                                .getMethod("getDynamicShortcuts", String::class.java, Int::class.java)
//                                .invoke(iShortcutManager, appInfo.packageName, context.userId) as ParceledListSlice<ShortcutInfo>
//
//                            manifest.list + dynamic.list
//                        }
//
//                        if (shortcuts.isNotEmpty()) {
//                            val app = apps[appInfo.packageName] ?: AppInfo(appName.toString(), appInfo).apply {
//                                apps[appInfo.packageName] = this
//                            }
//
//                            app.launcherShortcuts.addAll(
//                                shortcuts.map { shortcut ->
//                                    LauncherShortcutListInfo(
//                                        shortcutName = (shortcut.longLabel ?: shortcut.shortLabel).toString(),
//                                        icon = shortcut.icon,
//                                        appInfo = appInfo,
//                                        itemInfo = shortcut,
//                                    )
//                                }
//                            )
//                        }
//                    }
//                }
            }

            apps
        }

        items.clear()
        items.addAll(apps.values.sorted())
    }

    val filteredItems by remember(filter) {
        derivedStateOf {
            items.mapNotNull { app ->
                if (app.matchesFilter(filter)) {
                    app.copy(
                        widgets = TreeSet(app.widgets.filter { it.matchesFilter(filter) }),
                        shortcuts = TreeSet(app.shortcuts.filter { it.matchesFilter(filter) }),
                        launcherShortcuts = TreeSet(app.launcherShortcuts.filter {
                            it.matchesFilter(
                                filter
                            )
                        }),
                        launcherItems = TreeSet(app.launcherItems.filter { it.matchesFilter(filter) }),
                    )
                } else {
                    null
                }
            }
        }
    }

    return items to filteredItems
}
