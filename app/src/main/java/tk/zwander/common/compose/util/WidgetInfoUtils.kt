package tk.zwander.common.compose.util

import tk.zwander.common.data.AppInfo
import tk.zwander.common.data.BaseAppInfo
import tk.zwander.common.data.WidgetListFilters
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo
import tk.zwander.lockscreenwidgets.data.list.LauncherItemListInfo
import tk.zwander.lockscreenwidgets.data.list.LauncherShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo

fun AppInfo.matchesFilter(filter: String?, filters: WidgetListFilters): Boolean {
    if (matchesBaseFilter(filter) &&
        filters.currentCategories.run {
            isEmpty() || size == WidgetListFilters.Category.entries.size
        }
    ) return true
    if (widgets.any { it.matchesFilter(filter, filters) }) return true
    if (shortcuts.any { it.matchesFilter(filter, filters) }) return true
    if (launcherShortcuts.any { it.matchesFilter(filter, filters) }) return true
    if (launcherItems.any { it.matchesFilter(filter, filters) }) return true

    return false
}

fun BaseAppInfo<*>.matchesBaseFilter(filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (appName.contains(filter, true)) return true
    if (filter.contains(appName, true)) return true
    return false
}

fun BaseListInfo<*>.matchesFilter(filter: String?, filters: WidgetListFilters): Boolean {
    if (filters.currentCategories.isNotEmpty()) {
        val which = when (this) {
            is WidgetListInfo -> WidgetListFilters.Category.WIDGETS
            is ShortcutListInfo, is LauncherShortcutListInfo -> WidgetListFilters.Category.SHORTCUTS
            is LauncherItemListInfo -> WidgetListFilters.Category.LAUNCHERS
        }

        if (!filters.currentCategories.contains(which)) {
            return false
        }
    }

    if (filter.isNullOrBlank()) return true
    if (appInfo.matchesBaseFilter(filter)) return true
    if (name.contains(filter, true)) return true
    if (filter.contains(name, true)) return true
    return false
}
