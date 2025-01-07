package tk.zwander.common.util

import android.content.ComponentName
import android.content.pm.ComponentInfo

val ComponentInfo.componentNameCompat: ComponentName
    get() = ComponentName(packageName, name)
