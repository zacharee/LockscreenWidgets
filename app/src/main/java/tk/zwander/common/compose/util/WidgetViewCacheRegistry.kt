package tk.zwander.common.compose.util

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.ViewGroup
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.safeApplicationContext
import tk.zwander.common.util.themedContext

val Context.widgetViewCacheRegistry: WidgetViewCacheRegistry
    get() = WidgetViewCacheRegistry.getInstance(this)

class WidgetViewCacheRegistry private constructor(@Suppress("unused") private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: WidgetViewCacheRegistry? = null

        @Synchronized
        fun getInstance(context: Context): WidgetViewCacheRegistry {
            return instance ?: WidgetViewCacheRegistry(context.safeApplicationContext).also {
                instance = it
            }
        }
    }

    private val cachedViews = HashMap<Int, AppWidgetHostView>()

    fun getOrCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView {
        val widgetContext = try {
            context.safeApplicationContext.themedContext.createApplicationContext(
                appWidget.providerInfo.applicationInfo,
                Context.CONTEXT_INCLUDE_CODE,
            )
        } catch (e: Throwable) {
            context.logUtils.debugLog("Unable to create application context for " +
                    "${appWidget.providerInfo.applicationInfo.packageName}", e)
            null
        }

        return cachedViews.getOrPut(appWidgetId) {
            context.widgetHostCompat.createView(
                widgetContext ?: context, appWidgetId, appWidget,
            )
        }.also {
            (it.parent as? ViewGroup?)?.removeView(it)
        }
    }

    fun removeView(appWidgetId: Int) {
        cachedViews.remove(appWidgetId)
    }
}
