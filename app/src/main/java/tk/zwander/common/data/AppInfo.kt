package tk.zwander.common.data

import android.content.pm.ApplicationInfo
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo
import java.util.TreeSet

/**
 * Hold info for each app that has widgets, along with their
 * respective widgets
 *
 * @property appName the label of the app in question
 * @property appInfo the information about the app in question
 * @property widgets the widgets Lockscreen Widgets found for this app
 */
data class AppInfo(
    var appName: String,
    var appInfo: ApplicationInfo,
    var widgets: TreeSet<WidgetListInfo> = TreeSet(),
    var shortcuts: TreeSet<ShortcutListInfo> = TreeSet()
) : Comparable<AppInfo> {
    override fun compareTo(other: AppInfo) =
        appName.compareTo(other.appName, true)
}