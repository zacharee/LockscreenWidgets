package tk.zwander.common.compose.util

import tk.zwander.common.data.AppInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo

fun AppInfo.matchesFilter(filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (appName.contains(filter, true)) return true
    if (widgets.any { it.matchesFilter(filter) }) return true

    return false
}

fun WidgetListInfo.matchesFilter(filter: String?): Boolean {
    if (filter.isNullOrBlank()) return true
    if (name.contains(filter, true)) return true
    return false
}
