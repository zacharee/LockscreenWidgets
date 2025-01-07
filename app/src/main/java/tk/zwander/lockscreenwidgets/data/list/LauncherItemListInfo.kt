package tk.zwander.lockscreenwidgets.data.list

import android.content.pm.ResolveInfo
import androidx.core.graphics.drawable.IconCompat
import tk.zwander.common.data.BaseAppInfo
import tk.zwander.common.util.componentNameCompat
import java.util.Objects

class LauncherItemListInfo(
    appName: String,
    icon: IconCompat?,
    appInfo: BaseAppInfo<*>,
    itemInfo: ResolveInfo,
) : BaseListInfo<ResolveInfo>(
    appName, icon, appInfo, itemInfo,
) {
    override fun equals(other: Any?): Boolean {
        return super.equals(other) &&
                other is LauncherItemListInfo &&
                itemInfo.activityInfo.componentNameCompat == other.itemInfo.activityInfo.componentNameCompat
    }

    override fun hashCode(): Int {
        return super.hashCode() + Objects.hash(itemInfo.activityInfo.componentNameCompat)
    }
}
