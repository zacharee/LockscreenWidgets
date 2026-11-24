package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import dev.zwander.lswinterconnect.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Wraps a possibly-null bitmap so that a failed fetch can be cached too.
     * A plain [ConcurrentHashMap] can't store null values.
     *
     * Failed fetches carry a timestamp so they can expire; successful fetches
     * are only invalidated by an actual wallpaper change.
     */
    private class CachedWallpaper(
        val bitmap: Bitmap?,
        val timestamp: Long = SystemClock.elapsedRealtime(),
    ) {
        fun isExpired(now: Long): Boolean {
            return bitmap == null && now - timestamp >= NEGATIVE_CACHE_TTL_MS
        }

        companion object {
            private const val NEGATIVE_CACHE_TTL_MS = 30_000L
        }
    }

    private val cachedWallpapers = ConcurrentHashMap<Int, CachedWallpaper>()

    private val fetchMutexes = ConcurrentHashMap<Int, Mutex>()

    private val directListenerRegistered = atomic(false)

    private val wallpaperListener = object : IWallpaperListener.Stub() {
        override fun onWallpaperChanged() {
            peekLogUtils?.debugLog("Wallpaper changed, clearing cache.", null)
            cachedWallpapers.clear()
        }
    }

    suspend fun getWallpaperBitmap(flag: Int): Bitmap? {
        return fetchMutexes.getOrPut(flag) { Mutex() }.withLock {
            val now = SystemClock.elapsedRealtime()
            cachedWallpapers[flag]
                ?.takeUnless { it.isExpired(now) }
                ?.let { return@withLock it.bitmap }

            val bitmap = when {
                wallpaperServer.value != null -> {
                    try {
                        runCatching {
                            wallpaperServer.value?.getWallpaperPfd(flag)
                                ?.use { pfd ->
                                    pfd.fileDescriptor?.let { fd ->
                                        BitmapFactory.decodeFileDescriptor(fd)
                                    }
                                }
                        }.getOrNull() ?: wallpaperServer.value?.getWallpaper(flag)
                    } catch (e: Throwable) {
                        context.logUtils.debugLog("Unable to retrieve wallpaper from server", e)
                        null
                    }
                }

                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                    ensureDirectListenerRegistered()
                    context.directWallpaperUtils.getWallpaperBitmap(flag)
                }

                else -> {
                    context.logUtils.debugLog("Unable to retrieve wallpaper", null)
                    null
                }
            }

            if (bitmap != null) {
                peekLogUtils?.debugLog("Caching new wallpaper $bitmap.", null)
            } else {
                peekLogUtils?.debugLog("Caching missing wallpaper for flag $flag.", null)
            }

            cachedWallpapers[flag] = CachedWallpaper(bitmap)
            bitmap
        }
    }

    /**
     * Clear our cache whenever the direct wallpaper API sees a change,
     * but only if we've actually fallen back to it.
     */
    private fun ensureDirectListenerRegistered() {
        if (directListenerRegistered.compareAndSet(expect = false, update = true)) {
            context.directWallpaperUtils.registerChangeListener {
                peekLogUtils?.debugLog("Wallpaper changed, clearing cache.", null)
                cachedWallpapers.clear()
            }
        }
    }

    fun wallpaperServerConnected(server: IWallpaperAPI) {
        try {
            wallpaperServer.value = server
            // A failed fetch may have been cached while no server was available.
            cachedWallpapers.clear()
            server.registerWallpaperListener(wallpaperListener)
        } catch (e: Throwable) {
            context.logUtils.debugLog("Unable to bind wallpaper server", e)
        }
    }

    fun wallpaperServerDisconnected() {
        wallpaperServer.value = null
        cachedWallpapers.clear()
    }
}
