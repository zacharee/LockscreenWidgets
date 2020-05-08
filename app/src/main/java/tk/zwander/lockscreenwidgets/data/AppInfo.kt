package tk.zwander.lockscreenwidgets.data

import android.content.pm.ApplicationInfo

data class AppInfo(
    var appName: String,
    var appInfo: ApplicationInfo,
    var widgets: ArrayList<WidgetListInfo> = ArrayList()
) : Comparable<AppInfo> {
    override fun compareTo(other: AppInfo) =
        appName.compareTo(other.appName)
}