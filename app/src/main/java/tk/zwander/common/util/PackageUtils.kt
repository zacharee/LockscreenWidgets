package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle

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

fun PackageManager.getReceiverInfoCompat(componentName: ComponentName, flags: Int = 0): ActivityInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getReceiverInfo(componentName, PackageManager.ComponentInfoFlags.of(flags.toLong()))
    } else {
        getReceiverInfo(componentName, flags)
    }
}

@Composable
fun rememberPackageInstallationStatus(packageName: String): Boolean {
    val context = LocalContext.current

    fun checkStatus(): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    val state = remember {
        mutableStateOf(checkStatus())
    }

    LifecycleEffect(Lifecycle.State.RESUMED) {
        state.value = checkStatus()
    }

    return state.value
}
