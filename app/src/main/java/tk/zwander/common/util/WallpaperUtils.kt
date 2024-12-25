package tk.zwander.common.util

import android.annotation.SuppressLint
import android.app.IWallpaperManager
import android.app.IWallpaperManagerCallback
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import androidx.annotation.RequiresApi

val Context.wallpaperUtils: WallpaperUtils
    get() = WallpaperUtils.getInstance(this)

class WallpaperUtils private constructor(context: Context) : ContextWrapper(context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: WallpaperUtils? = null

        @Synchronized
        fun getInstance(context: Context): WallpaperUtils {
            return instance ?: WallpaperUtils(context.safeApplicationContext).apply {
                instance = this
            }
        }
    }

    private val iWallpaper = IWallpaperManager.Stub.asInterface(
        ServiceManager.getService(Context.WALLPAPER_SERVICE)
    )
    private val wallpaper = getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    private val callback = object : IWallpaperManagerCallback.Stub() {
        // "Fix" for Huawei.
        @Suppress("unused")
        fun onBlurWallpaperChanged() {}
        override fun onWallpaperColorsChanged(colors: WallpaperColors?, which: Int, userId: Int) {}

        override fun onWallpaperChanged() {
            logUtils.debugLog("Wallpaper changed, clearing cache.")
            cachedWallpaper = null
        }
    }
    private val handler = Handler(Looper.getMainLooper())

    private var cachedWallpaper: Bitmap? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            wallpaper.addOnColorsChangedListener({ _, _ -> cachedWallpaper = null }, handler)
        }
    }

    val wallpaperDrawable: Drawable?
        @SuppressLint("MissingPermission")
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                peekWallpaperBitmap()?.let {
                    BitmapDrawable(resources, it)
                } ?: wallpaper.drawable
            } else {
                wallpaper.drawable
            }
        }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun peekWallpaperBitmap(): Bitmap? {
        return if (cachedWallpaper != null && cachedWallpaper?.isRecycled == false) {
            logUtils.debugLog("Using cached wallpaper.")
            cachedWallpaper
        } else {
            logUtils.debugLog("Retrieving new wallpaper; isRecycled: ${cachedWallpaper?.isRecycled}.")
            val lockWallpaper = getWallpaper(WallpaperManager.FLAG_LOCK)
            val systemWallpaper = getWallpaper(WallpaperManager.FLAG_SYSTEM)
            val desc = lockWallpaper ?: systemWallpaper

            try {
                desc?.let { pfd ->
                    pfd.fileDescriptor?.let { fd ->
                        BitmapFactory.decodeFileDescriptor(fd)?.also { bmp ->
                            logUtils.debugLog("Caching new wallpaper $bmp.")
                            cachedWallpaper = bmp
                        }
                    }
                }
            } finally {
                lockWallpaper?.close()
                systemWallpaper?.close()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getWallpaper(flag: Int): ParcelFileDescriptor? {
        val bundle = Bundle()

        @Suppress("DEPRECATION")
        fun old(): ParcelFileDescriptor? {
            return iWallpaper.getWallpaper(
                packageName,
                callback,
                flag,
                bundle,
                userId,
            )
        }

        @RequiresApi(Build.VERSION_CODES.R)
        fun withFeature(): ParcelFileDescriptor? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    iWallpaper.getWallpaperWithFeature(
                        packageName,
                        attributionTag,
                        callback,
                        flag,
                        bundle,
                        userId,
                        true,
                    )
                } else {
                    iWallpaper::class.java.getMethod(
                        "getWallpaperWithFeature",
                        String::class.java, String::class.java,
                        IWallpaperManagerCallback::class.java,
                        Int::class.java, Bundle::class.java, Int::class.java,
                    ).invoke(
                        iWallpaper,
                        packageName, attributionTag, callback,
                        flag, bundle, userId,
                    ) as? ParcelFileDescriptor
                }
            } catch (e: NoSuchMethodError) {
                logUtils.debugLog("Missing getWallpaperWithFeature, using getWallpaper instead.")
                old()
            }
        }

        //Even though this hidden method was added in Android Nougat,
        //some devices (SAMSUNG >_>) removed or changed it, so it won't
        //always work. Thus the try-catch.
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                withFeature() ?: old()
            } else {
                old()
            }
        } catch (e: Exception) {
            logUtils.normalLog("Error retrieving wallpaper", e)
            null
        } catch (e: NoSuchMethodError) {
            logUtils.normalLog("Error retrieving wallpaper", e)
            null
        }
    }
}
