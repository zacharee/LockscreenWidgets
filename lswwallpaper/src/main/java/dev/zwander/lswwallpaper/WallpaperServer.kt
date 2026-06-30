package dev.zwander.lswwallpaper

import android.content.Context
import android.graphics.Bitmap
import dev.zwander.lswinterconnect.DirectWallpaperUtils
import dev.zwander.lswinterconnect.IWallpaperAPI
import dev.zwander.lswinterconnect.IWallpaperListener
import dev.zwander.lswinterconnect.directWallpaperUtils

class WallpaperServer(private val context: Context) : IWallpaperAPI.Stub() {
    private val registeredListeners = mutableListOf<IWallpaperListener>()
    private val listenerAdapter: DirectWallpaperUtils.WallpaperChangedListener = {
        registeredListeners.forEach { it.onWallpaperChanged() }
    }

    override fun getWallpaper(flag: Int): Bitmap? {
        return context.directWallpaperUtils.getWallpaperBitmap(flag)
    }

    override fun peekWallpaper(flag: Int): Bitmap? {
        return context.directWallpaperUtils.peekWallpaperBitmap(flag)
    }

    override fun registerWallpaperListener(listener: IWallpaperListener) {
        if (registeredListeners.isEmpty()) {
            context.directWallpaperUtils.registerChangeListener(listenerAdapter)
        }
        registeredListeners.add(listener)
    }

    override fun unregisterWallpaperListener(listener: IWallpaperListener) {
        registeredListeners.remove(listener)
        if (registeredListeners.isEmpty()) {
            context.directWallpaperUtils.unregisterChangeListener(listenerAdapter)
        }
    }
}
