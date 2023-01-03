package tk.zwander.lockscreenwidgets.data.list

import android.content.pm.ApplicationInfo
import java.util.Objects

open class BaseListInfo<T : BaseListInfo<T>>(
    val name: String,
    val icon: Int,
    val appInfo: ApplicationInfo
) : Comparable<T> {
    override fun compareTo(other: T): Int {
        return name.compareTo(other.name, true)
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseListInfo<*>
                && name == other.name
                && icon == other.icon
                && appInfo.packageName == other.appInfo.packageName
    }

    override fun hashCode(): Int {
        return Objects.hash(name, icon, appInfo.packageName)
    }
}