package dev.zwander.lswwallpaper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

fun Context.launchPermissionActivity() {
    startActivity(
        Intent(this, PermissionRequestActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

val Context.hasReadExternalStorage: Boolean
    get() = checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
