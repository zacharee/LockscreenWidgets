package dev.zwander.lswwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.RemoteCallbackList
import dev.zwander.lswinterconnect.*

class WallpaperServer(private val context: Context) : IWallpaperAPI.Stub() {
    private val registeredListeners = RemoteCallbackList<IWallpaperListener>()
    private val listenerAdapter: DirectWallpaperUtils.WallpaperChangedListener = {
        val count = registeredListeners.beginBroadcast()

        for (i in 0 until count) {
            try {
                registeredListeners.getBroadcastItem(i).onWallpaperChanged()
            } catch (e: Throwable) {
                context.logUtils.debugLog("Error calling wallpaper change listener", e)
            }
        }

        registeredListeners.finishBroadcast()
    }

    override fun getApiVersion(): Int = API_VERSION

    override fun getWallpaper(flag: Int): Bitmap? {
        context.logUtils.debugLog("Getting wallpaper $flag", null)
        return context.directWallpaperUtils.getWallpaperBitmap(flag)
    }

    override fun peekWallpaper(flag: Int): Bitmap? {
        context.logUtils.debugLog("Peeking wallpaper $flag", null)
        return context.directWallpaperUtils.peekWallpaperBitmap(flag)
    }

    override fun getWallpaperPfd(flag: Int): ParcelFileDescriptor? {
        context.logUtils.debugLog("Getting wallpaper PFD $flag", null)
        return context.directWallpaperUtils.getWallpaperPfd(flag)
    }

    override fun peekWallpaperPfd(flag: Int): ParcelFileDescriptor? {
        context.logUtils.debugLog("Peeking wallpaper PFD $flag", null)
        return context.directWallpaperUtils.peekWallpaperPfd(flag)
    }

    override fun registerWallpaperListener(listener: IWallpaperListener) {
        context.logUtils.debugLog("Registering wallpaper listener $listener", null)

        context.directWallpaperUtils.registerChangeListener(listenerAdapter)
        registeredListeners.register(listener)
    }

    override fun unregisterWallpaperListener(listener: IWallpaperListener) {
        context.logUtils.debugLog("Unregistering wallpaper listener $listener", null)

        registeredListeners.unregister(listener)
        if (registeredListeners.registeredCallbackCount == 0) {
            context.directWallpaperUtils.unregisterChangeListener(listenerAdapter)
        }
    }

    fun onDestroy() {
        registeredListeners.kill()
        context.directWallpaperUtils.unregisterChangeListener(listenerAdapter)
        context.directWallpaperUtils.onDestroy()
    }
}
