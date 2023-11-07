package tk.zwander.lockscreenwidgets.data.list

import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.util.componentNameCompat
import java.util.*

/**
 * Hold the info for a shortcut listed in [AddWidgetActivity]
 *
 * @property shortcutName the label of the shortcut
 * @property icon the preview image of the widget
 * @property appInfo the information about the application this shortcut belongs to
 * @property itemInfo the information about the shortcut
 */
class ShortcutListInfo(
    shortcutName: String,
    icon: Int,
    appInfo: ApplicationInfo,
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