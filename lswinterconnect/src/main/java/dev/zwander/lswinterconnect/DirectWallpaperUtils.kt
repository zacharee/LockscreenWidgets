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
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import java.util.*

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
            clearPfdCache()
            synchronized(wallpaperChangedListeners) {
                wallpaperChangedListeners.forEach { it.onWallpaperChanged() }
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val wallpaperChangedListeners = Collections.synchronizedSet(
        mutableSetOf<WallpaperChangedListener>(),
    )

    private val cachedWallpapers: MutableMap<Int, Bitmap?> = Collections.synchronizedMap(
        mutableMapOf(),
    )

    private val cachedPfds: MutableMap<Int, ParcelFileDescriptor?> = Collections.synchronizedMap(
        mutableMapOf(),
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            wallpaper.addOnColorsChangedListener(
                { _, _ ->
                    cachedWallpapers.clear()
                    clearPfdCache()
                    synchronized(wallpaperChangedListeners) {
                        wallpaperChangedListeners.forEach { it.onWallpaperChanged() }
                    }
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
        @SuppressLint("MissingPermission")
        return peekWallpaperBitmap(flag) ?: wallpaper.bitmap
    }

    fun getWallpaperDrawable(flag: Int): Drawable? {
        return getWallpaperBitmap(flag)?.toDrawable(context.resources)
    }

    fun peekWallpaperPfd(flag: Int): ParcelFileDescriptor? {
        return cachedPfds[flag]
    }

    fun getWallpaperPfd(flag: Int): ParcelFileDescriptor? {
        peekWallpaperPfd(flag)?.let { return it }

        peekLogUtils?.debugLog("Retrieving new wallpaper PFD.", null)
        val lockWallpaper = getWallpaper(flag)
        return (lockWallpaper ?: getWallpaper(WallpaperManager.FLAG_SYSTEM)).also {
            cachedPfds[flag]?.close()
            cachedPfds[flag] = it
        }
    }

    @SuppressLint("InlinedApi")
    fun peekWallpaperBitmap(flag: Int): Bitmap? {
        return if (cachedWallpapers[flag] != null && cachedWallpapers[flag]?.isRecycled == false) {
            peekLogUtils?.debugLog("Using cached wallpaper.", null)
            cachedWallpapers[flag]
        } else {
            val desc = getWallpaperPfd(flag)

            synchronized(cachedPfds) {
                desc?.dup()?.use { pfd ->
                    pfd.fileDescriptor?.let { fd ->
                        BitmapFactory.decodeFileDescriptor(fd)?.also { bmp ->
                            peekLogUtils?.debugLog("Caching new wallpaper $bmp.", null)
                            cachedWallpapers[flag] = bmp
                        }
                    }
                }
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
            } catch (e: NoSuchMethodException) {
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
            } else {
                old() ?: preNougat()
            }
        } catch (e: Exception) {
            peekLogUtils?.debugLog("Error retrieving wallpaper", e)
            null
        } catch (e: NoSuchMethodError) {
            peekLogUtils?.debugLog("Error retrieving wallpaper", e)
            null
        }
    }

    fun onDestroy() {
        cachedWallpapers.clear()
        clearPfdCache()
    }

    private fun clearPfdCache() {
        synchronized(cachedPfds) {
            cachedPfds.forEach { [_, pfd] ->
                try {
                    pfd?.close()
                } catch (_: Throwable) {}
            }
            cachedPfds.clear()
        }
    }

    fun interface WallpaperChangedListener {
        fun onWallpaperChanged()
    }
}
