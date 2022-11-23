package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import tk.zwander.lockscreenwidgets.util.toBitmap

@SuppressLint("DiscouragedApi")
fun Context.getRemoteDrawable(
    resource: Intent.ShortcutIconResource?
) : Bitmap? {
    val remRes = packageManager.getResourcesForApplication(packageName)

    return getRemoteDrawable(
        packageName,
        resource?.let {
            remRes.getIdentifier(
                it.packageName,
                "drawable",
                it.resourceName
            )
        } ?: 0,
        remRes
    )
}

fun Context.getRemoteDrawable(
    packageName: String,
    resourceId: Int,
    remRes: Resources = packageManager.getResourcesForApplication(packageName)
): Bitmap? {
    val defaultGetter = { packageManager.getApplicationIcon(packageName) }

    val drawable = when (resourceId) {
        0 -> defaultGetter()
        else -> {
            try {
                ResourcesCompat.getDrawable(remRes, resourceId, remRes.newTheme()) ?: defaultGetter()
            } catch (e: Resources.NotFoundException) {
                defaultGetter()
            }
        }
    }

    return drawable?.mutate()?.toBitmap()?.run { copy(config, false) }
}
