package tk.zwander.lockscreenwidgets.data.list

import android.content.pm.ApplicationInfo
import java.util.*

open class BaseListInfo(
    val name: String,
    val icon: Int,
    val appInfo: ApplicationInfo
) : Comparable<BaseListInfo> {
    override fun compareTo(other: BaseListInfo): Int {
        return name.compareTo(other.name)
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseListInfo
                && name == other.name
                && icon == other.icon
                && appInfo.packageName == other.appInfo.packageName
    }

    override fun hashCode(): Int {
        return Objects.hash(name, icon, appInfo.packageName)
    }
}