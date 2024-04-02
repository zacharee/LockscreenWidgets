package tk.zwander.lockscreenwidgets.data.list

import android.os.Parcelable
import androidx.core.graphics.drawable.IconCompat
import tk.zwander.common.data.BaseAppInfo
import java.util.Objects

sealed class BaseListInfo<ItemInfo : Parcelable>(
    val name: String,
    val icon: IconCompat?,
    val appInfo: BaseAppInfo<*>,
    val itemInfo: ItemInfo,
) : Comparable<BaseListInfo<ItemInfo>> {
    override fun compareTo(other: BaseListInfo<ItemInfo>): Int {
        return name.compareTo(other.name, true)
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseListInfo<*>
                && name == other.name
                && icon?.toBundle() == other.icon?.toBundle()
                && appInfo.appInfo.packageName == other.appInfo.appInfo.packageName
    }

    override fun hashCode(): Int {
        return Objects.hash(name, icon?.toBundle(), appInfo.appInfo.packageName)
    }
}