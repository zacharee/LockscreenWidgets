package tk.zwander.common.data

import android.content.pm.ApplicationInfo
import tk.zwander.lockscreenwidgets.data.list.LauncherShortcutListInfo
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
    override val appName: String,
    override val appInfo: ApplicationInfo,
    val widgets: TreeSet<WidgetListInfo> = TreeSet(),
    val shortcuts: TreeSet<ShortcutListInfo> = TreeSet(),
    val launcherShortcuts: TreeSet<LauncherShortcutListInfo> = TreeSet(),
) : BaseAppInfo<AppInfo>()

data class BasicAppInfo(
    override val appName: String,
    override val appInfo: ApplicationInfo,
    val isChecked: Boolean,
) : BaseAppInfo<BasicAppInfo>() {
    override fun compareTo(other: BasicAppInfo): Int {
        val checkedComparison = when {
            isChecked && !other.isChecked -> -1
            !isChecked && other.isChecked -> 1
            else -> 0
        }

        if (checkedComparison != 0) {
            return checkedComparison
        }

        return super.compareTo(other)
    }
}

abstract class BaseAppInfo<T : BaseAppInfo<T>> : Comparable<T> {
    abstract val appName: String
    abstract val appInfo: ApplicationInfo

    override fun compareTo(other: T): Int {
        return appName.compareTo(other.appName, true)
    }
}