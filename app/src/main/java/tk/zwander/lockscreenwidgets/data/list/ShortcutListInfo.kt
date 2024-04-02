package tk.zwander.lockscreenwidgets.data.list

import android.content.pm.ResolveInfo
import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.data.BaseAppInfo
import tk.zwander.common.util.componentNameCompat
import java.util.Objects

/**
 * Hold the info for a shortcut listed in [AddWidgetActivity]
 *
 * @param shortcutName the label of the shortcut
 * @param icon the preview image of the widget
 * @param appInfo the information about the application this shortcut belongs to
 * @param itemInfo the information about the shortcut
 */
class ShortcutListInfo(
    shortcutName: String,
    icon: Int,
    appInfo: BaseAppInfo<*>,
    itemInfo: ResolveInfo
) : BaseListInfo<ResolveInfo, Int>(
    shortcutName, icon, appInfo, itemInfo
) {
    override fun equals(other: Any?): Boolean {
        return super.equals(other) &&
                other is ShortcutListInfo &&
                itemInfo.activityInfo.componentNameCompat == other.itemInfo.activityInfo.componentNameCompat
    }

    override fun hashCode(): Int {
        return super.hashCode() + Objects.hash(itemInfo.activityInfo.componentNameCompat)
    }
}