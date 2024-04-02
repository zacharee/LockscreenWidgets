package tk.zwander.lockscreenwidgets.data.list

import android.annotation.TargetApi
import android.content.pm.ShortcutInfo
import android.os.Build
import androidx.core.graphics.drawable.IconCompat
import tk.zwander.common.data.BaseAppInfo

@TargetApi(Build.VERSION_CODES.N_MR1)
class LauncherShortcutListInfo(
    shortcutName: String,
    icon: IconCompat?,
    appInfo: BaseAppInfo<*>,
    itemInfo: ShortcutInfo,
): BaseListInfo<ShortcutInfo>(
    shortcutName, icon, appInfo, itemInfo,
)
