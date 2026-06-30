package dev.zwander.lswinterconnect

import android.annotation.SuppressLint
import android.app.IWallpaperManager
import android.app.IWallpaperManagerCallback
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable

val Context.directWallpaperUtils: DirectWallpaperUtils
    get() = DirectWallpaperUtils.getInstance(this)

class DirectWallpaperUtils private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: DirectWallpaperUtils? = null

        @Synchronized
        fun getInstance(context: Context): DirectWallpaperUtils {
            return instance ?: DirectWallpaperUtils(context.safeApplicationContext).apply {
                instance = this
            }
        }
    }

    private val iWallpaper = IWallpaperManager.Stub.asInterface(
        ServiceManager.getService(Context.WALLPAPER_SERVICE)
    )
    private val wallpaper = context.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    private val callback = object : IWallpaperManagerCallback.Stub() {
        // "Fix" for Huawei.
        @Suppress("unused")
        fun onBlurWallpaperChanged() {}

        // "Fix" for Honor.
        @Suppress("unused")
        fun onWallpaperChanged(value: Int) {
            onWallpaperChanged()
        }

        override fun onWallpaperColorsChanged(colors: WallpaperColors?, which: Int, userId: Int) {}

        override fun onWallpaperChanged() {
            peekLogUtils?.debugLog("Wallpaper changed, clearing cache.", null)
            cachedWallpapers.clear()
            wallpaperChangedListeners.forEach { it.onWallpaperChanged() }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val wallpaperChangedListeners = mutableListOf<WallpaperChangedListener>()

    private var cachedWallpapers: MutableMap<Int, Bitmap?> = mutableMapOf()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            wallpaper.addOnColorsChangedListener(
                { _, _ ->
                    cachedWallpapers.clear()
                    wallpaperChangedListeners.forEach { it.onWallpaperChanged() }
                },
                handler,
            )
        }
    }

    fun registerChangeListener(listener: WallpaperChangedListener) {
        wallpaperChangedListeners.add(listener)
    }

    fun unregisterChangeListener(listener: WallpaperChangedListener) {
        wallpaperChangedListeners.remove(listener)
    }

    fun getWallpaperBitmap(flag: Int): Bitmap? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                @SuppressLint("MissingPermission")
                peekWallpaperBitmap(flag) ?: wallpaper.bitmap
            }

            else -> {
                @SuppressLint("MissingPermission")
                wallpaper.bitmap
            }
        }
    }

    fun getWallpaperDrawable(flag: Int): Drawable? {
        return getWallpaperBitmap(flag)?.toDrawable(context.resources)
    }

    @SuppressLint("InlinedApi")
    fun peekWallpaperBitmap(flag: Int): Bitmap? {
        return if (cachedWallpapers[flag] != null && cachedWallpapers[flag]?.isRecycled == false) {
            peekLogUtils?.debugLog("Using cached wallpaper.")
            cachedWallpapers[flag]
        } else {
            peekLogUtils?.debugLog("Retrieving new wallpaper; isRecycled: ${cachedWallpapers[flag]?.isRecycled}.")
            val lockWallpaper = getWallpaper(flag)
            val systemWallpaper = getWallpaper(WallpaperManager.FLAG_SYSTEM)
            val desc = lockWallpaper ?: systemWallpaper

            try {
                desc?.let { pfd ->
                    pfd.fileDescriptor?.let { fd ->
                        BitmapFactory.decodeFileDescriptor(fd)?.also { bmp ->
                            peekLogUtils?.debugLog("Caching new wallpaper $bmp.", null)
                            cachedWallpapers[flag] = bmp
                        }
                    }
                }
            } finally {
                lockWallpaper?.close()
                systemWallpaper?.close()
            }
        }
    }

    private fun getWallpaper(flag: Int): ParcelFileDescriptor? {
        val bundle = Bundle()

        fun preNougat(): ParcelFileDescriptor? {
            return iWallpaper::class.java.getMethod(
                "getWallpaper",
                IWallpaperManagerCallback::class.java,
                Bundle::class.java,
            ).invoke(
                iWallpaper,
                callback,
                bundle,
            ) as ParcelFileDescriptor?
        }

        @Suppress("DEPRECATION")
        fun old(): ParcelFileDescriptor? {
            return iWallpaper.getWallpaper(
                context.packageName,
                callback,
                flag,
                bundle,
                context.userId,
            )
        }

        @RequiresApi(Build.VERSION_CODES.R)
        fun withFeature(): ParcelFileDescriptor? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    iWallpaper.getWallpaperWithFeature(
                        context.packageName,
                        context.attributionTag,
                        callback,
                        flag,
                        bundle,
                        context.userId,
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
                        context.packageName,
                        @SuppressLint("NewApi")
                        context.attributionTag,
                        callback,
                        flag, bundle, context.userId,
                    ) as? ParcelFileDescriptor
                }
            } catch (e: NoSuchMethodError) {
                peekLogUtils?.debugLog("Missing getWallpaperWithFeature, using getWallpaper instead.", e)
                old()
            }
        }

        //Even though this hidden method was added in Android Nougat,
        //some devices (SAMSUNG >_>) removed or changed it, so it won't
        //always work. Thus the try-catch.
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                withFeature() ?: old() ?: preNougat()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                old() ?: preNougat()
            } else {
                preNougat()
            }
        } catch (e: Exception) {
            peekLogUtils?.debugLog("Error retrieving wallpaper", e)
            null
        } catch (e: NoSuchMethodError) {
            peekLogUtils?.debugLog("Error retrieving wallpaper", e)
            null
        }
    }

    fun interface WallpaperChangedListener {
        fun onWallpaperChanged()
    }
}
