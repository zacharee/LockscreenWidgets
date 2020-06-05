package tk.zwander.lockscreenwidgets.data

import android.content.pm.ApplicationInfo

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
    var widgets: ArrayList<WidgetListInfo> = ArrayList()
) : Comparable<AppInfo> {
    override fun compareTo(other: AppInfo) =
        appName.compareTo(other.appName)
}