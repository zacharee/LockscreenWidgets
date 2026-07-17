package tk.zwander.common.util

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import dev.zwander.lswinterconnect.safeApplicationContext
import tk.zwander.common.views.remote.CatchingListView
import tk.zwander.common.views.remote.CatchingTextClock
import tk.zwander.common.views.remote.LazyColumnListView
import tk.zwander.common.views.remote.LazyGridGridView
import tk.zwander.common.views.remote.NestedGridView
import tk.zwander.lockscreenwidgets.App

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
                    "ListView" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        FrameLayout(context).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            val composeView = ComposeView(object : ContextWrapper(context) {
                                override fun getApplicationContext(): Context {
                                    return object : ContextWrapper(super.applicationContext) {
                                        override fun registerComponentCallbacks(callback: ComponentCallbacks?) {
                                            try {
                                                super.registerComponentCallbacks(callback)
                                            } catch (e: Throwable) {
                                                context.logUtils.debugLog("Unable to register component callbacks", e)
                                                App.instance.registerComponentCallbacks(callback)
                                            }
                                        }

                                        override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {
                                            try {
                                                super.unregisterComponentCallbacks(callback)
                                            } catch (e: Throwable) {
                                                context.logUtils.debugLog("Unable to unregister component callbacks", e)
                                                App.instance.unregisterComponentCallbacks(callback)
                                            }
                                        }
                                    }
                                }
                            }).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            }
                            addView(LazyColumnListView(context, attrs, composeView))
                            addView(composeView)
                        }
                    } else {
                        CatchingListView(context, attrs, widgetId)
                    }
                    "TextClock" -> CatchingTextClock(context, attrs)
                    "GridView" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        LazyGridGridView(context, attrs)
                    } else {
                        NestedGridView(context, attrs)
                    }
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
        return super.getApplicationContext() ?: super.baseContext.safeApplicationContext
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
