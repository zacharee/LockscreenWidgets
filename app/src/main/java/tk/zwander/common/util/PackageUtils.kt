@file:Suppress("DEPRECATION")

package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.os.Build

fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int = 0): ApplicationInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        getApplicationInfo(packageName, flags)
    }
}

fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int = 0): List<ResolveInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        queryIntentActivities(intent, flags)
    }
}

fun PackageManager.getInstalledApplicationsCompat(flags: Int = 0): List<ApplicationInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        getInstalledApplications(flags)
    }
}

fun PackageManager.getResourcesForApplicationInAnyState(packageName: String): Resources {
    return getResourcesForApplication(getApplicationInfoInAnyState(packageName))
}

@SuppressLint("InlinedApi")
fun PackageManager.getApplicationInfoInAnyState(packageName: String): ApplicationInfo {
    return getApplicationInfoCompat(
        packageName = packageName,
        flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_ALL
    )
}
