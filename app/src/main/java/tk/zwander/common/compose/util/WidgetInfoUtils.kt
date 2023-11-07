package tk.zwander.common.compose.util

import tk.zwander.common.data.AppInfo
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo

fun AppInfo.matchesFilter(filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (appName.contains(filter, true)) return true
    if (widgets.any { it.matchesFilter(filter) }) return true
    if (shortcuts.any { it.matchesFilter(filter) }) return true
    if (launcherShortcuts.any { it.matchesFilter(filter) }) return true

    return false
}

fun BaseListInfo<*, *>.matchesFilter(filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (name.contains(filter, true)) return true
    return false
}
