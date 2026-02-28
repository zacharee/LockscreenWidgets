package tk.zwander.common.util

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import tk.zwander.common.views.remote.CatchingListView
import tk.zwander.common.views.remote.CatchingTextClock

class RemoteViewsLayoutInflaterContext(
    context: Context,
    private val widgetId: Int,
) : ContextWrapper(context) {
    private val factory by lazy {
        object : LayoutInflater.Factory2 {
            override fun onCreateView(
                parent: View?,
                name: String,
                context: Context,
                attrs: AttributeSet,
            ): View? {
                logUtils.debugLog("onCreateView(${name}) for widget ${getWidgetData()}", null)
                return when (name) {
                    "ListView" -> CatchingListView(context, attrs, widgetId)
                    "TextClock" -> CatchingTextClock(context, attrs)
                    else -> null
                }
            }

            override fun onCreateView(
                name: String,
                context: Context,
                attrs: AttributeSet,
            ): View? {
                return onCreateView(null, name, context, attrs)
            }
        }
    }

    private val inflater: LayoutInflater by lazy {
        LayoutInflater.from(context)
            .cloneInContext(this)
            .also { layoutInflater ->
                layoutInflater.factory2 = factory
            }
    }

    override fun getSystemService(name: String): Any? {
        if (LAYOUT_INFLATER_SERVICE == name) {
            return inflater
        }

        return super.getSystemService(name)
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext() ?: baseContext
    }

    private fun getWidgetData(): String {
        return try {
            appWidgetManager.getAppWidgetInfo(widgetId)
                ?.provider
                ?.flattenToString()
                ?: "$widgetId"
        } catch (e: Throwable) {
            logUtils.debugLog("Error getting widget data in inflater", e)
            "$widgetId"
        }
    }
}
