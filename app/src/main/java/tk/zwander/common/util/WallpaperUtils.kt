package tk.zwander.common.util

import android.app.IWallpaperManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
fun Context.getWallpaper(flag: Int, wallpaperService: IWallpaperManager): ParcelFileDescriptor? {
    val bundle = Bundle()

    //Even though this hidden method was added in Android Nougat,
    //some devices (SAMSUNG >_>) removed or changed it, so it won't
    //always work. Thus the try-catch.
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wallpaperService.getWallpaperWithFeature(
                packageName,
                attributionTag,
                null,
                flag,
                bundle,
                UserHandle.getCallingUserId()
            )
        } else {
            @Suppress("DEPRECATION")
            wallpaperService.getWallpaper(
                packageName,
                null,
                flag,
                bundle,
                UserHandle.getCallingUserId()
            )
        }
    } catch (e: Exception) {
        logUtils.debugLog("Error retrieving wallpaper", e)
        null
    } catch (e: NoSuchMethodError) {
        logUtils.debugLog("Error retrieving wallpaper", e)
        null
    }
}
