package tk.zwander.common.views.remote

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView
import tk.zwander.common.util.BugsnagUtils
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.logUtils

class CatchingListView(
    context: Context,
    attrs: AttributeSet,
    private val widgetId: Int,
) : ListView(context, attrs) {
    override fun layoutChildren() {
        adapter?.let { adapter ->
            if (count != adapter.count) {
                context.logUtils.debugLog(
                    message = "Mismatch in listview count ($count) and adapter count (${adapter.count}) " +
                            "for ListView with ID name ${safeViewIdName()}.\n" +
                            "Widget ID: $widgetId.\n" +
                            "Widget provider: ${findAppWidgetProvider()}",
                    throwable = null,
                )
            }
        }

        try {
            super.layoutChildren()
        } catch (e: IllegalStateException) {
            BugsnagUtils.notify(e)
        }
    }

    private fun safeViewIdName(): String? {
        return try {
            context.resources.getResourceName(id)
        } catch (_: Throwable) {
            null
        }
    }

    private fun findAppWidgetProvider(): String? {
        return try {
            context.appWidgetManager.getAppWidgetInfo(widgetId)
                ?.provider?.flattenToString()
        } catch (_: Throwable) {
            null
        }
    }
}