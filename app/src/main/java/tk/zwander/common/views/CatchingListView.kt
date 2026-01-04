package tk.zwander.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewParent
import android.widget.ListView
import android.widget.RemoteViews
import tk.zwander.common.util.BugsnagUtils
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.logUtils

class CatchingListView(context: Context, attrs: AttributeSet) : ListView(context, attrs) {
    override fun layoutChildren() {
        context.logUtils.normalLog("LAYING OUT", null)
        if (count != adapter.count) {
            context.logUtils.debugLog(
                message = "Mismatch in listview count ($count) and adapter count (${adapter.count}) " +
                        "for ListView with ID name ${context.resources.getResourceName(id)}.\n" +
                        "Widget info: ${widgetInfo()}\n" +
                        "Widget provider: ${findAppWidgetProvider()}",
                throwable = null,
            )
        }

        try {
            super.layoutChildren()
        } catch (e: IllegalStateException) {
            BugsnagUtils.notify(e)
        }
    }

    private fun widgetInfo(): String? {
        try {
            for (i in 0 until adapter.count) {
                val views = (adapter.getItem(i) as? RemoteViews)

                views?.let { remoteViews ->
                    "${remoteViews.`package`}/" +
                            "${
                                try {
                                    context.packageManager
                                        .getResourcesForApplication(remoteViews.mApplication)
                                        .getResourceName(remoteViews.viewId)
                                } catch (_: Throwable) {
                                    null
                                }
                            }"
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    private fun findAppWidgetProvider(): String? {
        return try {
            val hostView = findHostView(parent)
            hostView?.appWidgetId?.let {
                context.appWidgetManager.getAppWidgetInfo(it)
                    ?.provider?.flattenToString()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun findHostView(parent: ViewParent?): ZeroPaddingAppWidgetHostView? {
        return when (parent) {
            is ZeroPaddingAppWidgetHostView -> parent
            null -> null
            else -> findHostView(parent)
        }
    }
}
