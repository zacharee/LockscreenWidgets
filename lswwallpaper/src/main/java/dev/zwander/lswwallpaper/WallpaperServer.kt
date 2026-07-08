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
        registeredListeners.removeIf { !it.asBinder().isBinderAlive }
        registeredListeners.forEach {
            try {
                it.onWallpaperChanged()
            } catch (e: Throwable) {
                context.logUtils.debugLog("Error calling wallpaper change listener", e)
            }
        }
    }

    override fun getWallpaper(flag: Int): Bitmap? {
        context.logUtils.debugLog("Getting wallpaper $flag")
        return context.directWallpaperUtils.getWallpaperBitmap(flag)
    }

    override fun peekWallpaper(flag: Int): Bitmap? {
        context.logUtils.debugLog("Peeking wallpaper $flag")
        return context.directWallpaperUtils.peekWallpaperBitmap(flag)
    }

    override fun registerWallpaperListener(listener: IWallpaperListener) {
        context.logUtils.debugLog("Registering wallpaper listener $listener")

        if (registeredListeners.isEmpty()) {
            context.directWallpaperUtils.registerChangeListener(listenerAdapter)
        }
        registeredListeners.add(listener)
    }

    override fun unregisterWallpaperListener(listener: IWallpaperListener) {
        context.logUtils.debugLog("Unregistering wallpaper listener $listener")

        registeredListeners.remove(listener)
        if (registeredListeners.isEmpty()) {
            context.directWallpaperUtils.unregisterChangeListener(listenerAdapter)
        }
    }
}
