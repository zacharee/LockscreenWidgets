package tk.zwander.common.compose.util

import tk.zwander.common.data.AppInfo
import tk.zwander.common.data.BaseAppInfo
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo

fun AppInfo.matchesFilter(filter: String?): Boolean {
    if (matchesBaseFilter(filter)) return true
    if (widgets.any { it.matchesFilter(filter) }) return true
    if (shortcuts.any { it.matchesFilter(filter) }) return true
    if (launcherShortcuts.any { it.matchesFilter(filter) }) return true

    return false
}

fun BaseAppInfo<*>.matchesBaseFilter(filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (appName.contains(filter, true)) return true
    if (filter.contains(appName, true)) return true
    return false
}

fun BaseListInfo<*, *>.matchesFilter(filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (appInfo.matchesBaseFilter(filter)) return true
    if (name.contains(filter, true)) return true
    if (filter.contains(name, true)) return true
    return false
}
