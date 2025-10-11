package tk.zwander.common.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

val Context.hasStoragePermission: Boolean
    get() = checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

val Context.hasReadMediaImagesPermission: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkCallingOrSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

val Context.canReadWallpaper: Boolean
    get() = hasStoragePermission && hasReadMediaImagesPermission