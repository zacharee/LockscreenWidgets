package tk.zwander.lockscreenwidgets.data.list

import android.content.pm.ApplicationInfo
import android.os.Parcelable
import java.util.Objects

sealed class BaseListInfo<ItemInfo : Parcelable, Icon : Any>(
    val name: String,
    val icon: Icon?,
    val appInfo: ApplicationInfo,
    val itemInfo: ItemInfo,
) : Comparable<BaseListInfo<ItemInfo, Icon>> {
    override fun compareTo(other: BaseListInfo<ItemInfo, Icon>): Int {
        return name.compareTo(other.name, true)
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseListInfo<*, *>
                && name == other.name
                && icon == other.icon
                && appInfo.packageName == other.appInfo.packageName
    }

    override fun hashCode(): Int {
        return Objects.hash(name, icon, appInfo.packageName)
    }
}