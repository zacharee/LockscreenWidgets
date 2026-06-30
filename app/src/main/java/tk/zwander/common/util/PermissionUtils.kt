package tk.zwander.common.util

import android.content.Context
import android.content.pm.PackageManager

val Context.hasStoragePermission: Boolean
    get() = checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

val Context.canReadWallpaper: Boolean
    get() = hasStoragePermission