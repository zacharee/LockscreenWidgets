package tk.zwander.lockscreenwidgets.data.list

import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import tk.zwander.lockscreenwidgets.activities.add.AddWidgetActivity
import java.util.*

/**
 * Hold the info for a shortcut listed in [AddWidgetActivity]
 *
 * @property shortcutName the label of the shortcut
 * @property icon the preview image of the widget
 * @property shortcutInfo the information about the shortcut
 * @property appInfo the information about the application this shortcut belongs to
 */
class ShortcutListInfo(
    shortcutName: String,
    icon: Int,
    var shortcutInfo: ResolveInfo,
    appInfo: ApplicationInfo
) : BaseListInfo(
    shortcutName, icon, appInfo
) {
    override fun equals(other: Any?): Boolean {
        return super.equals(other) &&
                shortcutInfo.componentInfo.componentName ==
                (other as ShortcutListInfo).shortcutInfo.componentInfo.componentName
    }

    override fun hashCode(): Int {
        return super.hashCode() + Objects.hash(shortcutInfo.componentInfo.componentName)
    }
}