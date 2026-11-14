package tk.zwander.common.util

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import dev.zwander.lswinterconnect.safeApplicationContext
import java.util.concurrent.ConcurrentHashMap

val Context.appWidgetInfoCache: AppWidgetInfoCache
    get() = AppWidgetInfoCache.getInstance(this)

/**
 * Keeps a process-wide cache of [AppWidgetProviderInfo] objects.
 *
 * [android.appwidget.AppWidgetManager.getAppWidgetInfo] is a binder call, so it can take
 * a long time to execute. Widget views are created and updated often, so any main-thread
 * binder call there can cause freezes and ANRs. Cache provider infos when they're retrieved
 * through an IPC so they can be referenced more quickly.
 */
class AppWidgetInfoCache private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: AppWidgetInfoCache? = null

        @Synchronized
        fun getInstance(context: Context): AppWidgetInfoCache {
            return instance ?: AppWidgetInfoCache(context.safeApplicationContext).also {
                instance = it
            }
        }
    }

    private val cache = ConcurrentHashMap<Int, AppWidgetProviderInfo>()

    fun getCached(appWidgetId: Int): AppWidgetProviderInfo? {
        return cache[appWidgetId]
    }

    operator fun set(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        if (info == null) {
            cache.remove(appWidgetId)
        } else {
            cache[appWidgetId] = info
        }
    }

    fun remove(appWidgetId: Int) {
        cache.remove(appWidgetId)
    }

    fun clear() {
        cache.clear()
    }

    fun getOrFetch(appWidgetId: Int): AppWidgetProviderInfo? {
        cache[appWidgetId]?.let { return it }

        return fetchAndCache(appWidgetId)
    }

    /**
     * Explicitly fetch the provider info and save it to cache, overwriting
     * any existing provider info.
     */
    fun fetchAndCache(appWidgetId: Int): AppWidgetProviderInfo? {
        return try {
            context.appWidgetManager.getAppWidgetInfo(appWidgetId)?.also {
                cache[appWidgetId] = it
            }
        } catch (e: Throwable) {
            context.logUtils.debugLog("Unable to get app widget info for $appWidgetId", e)
            null
        }
    }
}
