package tk.zwander.lockscreenwidgets.data.list

import android.annotation.TargetApi
import android.content.pm.ApplicationInfo
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.Build

@TargetApi(Build.VERSION_CODES.N_MR1)
class LauncherShortcutListInfo(
    shortcutName: String,
    icon: Icon?,
    appInfo: ApplicationInfo,
    itemInfo: ShortcutInfo,
): BaseListInfo<ShortcutInfo, Icon>(
    shortcutName, icon, appInfo, itemInfo,
)
