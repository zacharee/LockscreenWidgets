package tk.zwander.common.util

import android.content.ComponentName
import android.content.pm.ComponentInfo
import android.content.pm.ResolveInfo

val ResolveInfo.componentInfoCompat: ComponentInfo
    get() = activityInfo
        ?: serviceInfo
        ?: providerInfo
        ?: throw IllegalStateException("Missing ComponentInfo!")

val ComponentInfo.componentNameCompat: ComponentName
    get() = ComponentName(packageName, name)
