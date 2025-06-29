package tk.zwander.common.compose.util

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.ViewGroup
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.safeApplicationContext

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
        return cachedViews[appWidgetId]?.also {
            if (it.parent != null) {
                (it.parent as ViewGroup).removeView(it)
            }
        } ?: context.widgetHostCompat.createView(
            context, appWidgetId, appWidget,
        ).also {
            cachedViews[appWidgetId] = it
        }
    }

    fun removeView(appWidgetId: Int) {
        cachedViews.remove(appWidgetId)
    }
}
