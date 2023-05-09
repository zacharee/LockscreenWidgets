package tk.zwander.common.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment

val Context.hasStoragePermission: Boolean
    get() = when {
        applicationInfo.targetSdkVersion >= Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            Environment.isExternalStorageManager()
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        else -> true
    }