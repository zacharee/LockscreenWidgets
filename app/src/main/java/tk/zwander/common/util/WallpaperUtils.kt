package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toDrawable
import dev.zwander.lswinterconnect.IWallpaperAPI
import dev.zwander.lswinterconnect.IWallpaperListener
import dev.zwander.lswinterconnect.directWallpaperUtils
import dev.zwander.lswinterconnect.peekLogUtils
import dev.zwander.lswinterconnect.safeApplicationContext
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

val Context.wallpaperUtils: WallpaperUtils
    get() = WallpaperUtils.getInstance(this)

class WallpaperUtils private constructor(
    private val context: Context,
) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: WallpaperUtils? = null

        @Synchronized
        fun getInstance(
            context: Context,
        ): WallpaperUtils {
            return instance ?: WallpaperUtils(
                context.safeApplicationContext,
            ).apply {
                instance = this
            }
        }
    }

    private val wallpaperServer: AtomicRef<IWallpaperAPI?> = atomic(null)

    private val cachedWallpapers = mutableMapOf<Int, Drawable?>()

    private val wallpaperListener = object : IWallpaperListener.Stub() {
        override fun onWallpaperChanged() {
            peekLogUtils?.debugLog("Wallpaper changed, clearing cache.", null)
            cachedWallpapers.clear()
        }
    }

    fun getWallpaperDrawable(flag: Int): Drawable? {
        return when {
            cachedWallpapers[flag] != null -> {
                cachedWallpapers[flag]
            }
            wallpaperServer.value != null -> {
                wallpaperServer.value?.getWallpaper(flag)
                    ?.toDrawable(context.resources)
                    ?.also { cachedWallpapers[flag] = it }
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                context.directWallpaperUtils.getWallpaperDrawable(flag)
                    ?.also { cachedWallpapers[flag] = it }
            }
            else -> {
                context.logUtils.debugLog("Unable to retrieve wallpaper", null)
                null
            }
        }
    }

    fun wallpaperServerConnected(server: IWallpaperAPI) {
        wallpaperServer.value = server
        server.registerWallpaperListener(wallpaperListener)
    }

    fun wallpaperServerDisconnected() {
        wallpaperServer.value = null
    }
}
