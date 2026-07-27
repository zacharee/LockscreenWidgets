package tk.zwander.common.views.remote

import android.widget.AbsListView
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.logUtils

interface BaseListView {
    fun init(listView: AbsListView)

    fun safeViewIdName(): String?

    fun findAppWidgetProvider(): String?

    fun shouldLayout(): Boolean
}

class BaseListViewClass(private val widgetId: Int) : BaseListView {
    private lateinit var listView: AbsListView

    override fun init(listView: AbsListView) {
        this.listView = listView
    }

    override fun safeViewIdName(): String? {
        return try {
            listView.context.resources.getResourceName(listView.id)
        } catch (_: Throwable) {
            listView.id.toString()
        }
    }

    override fun findAppWidgetProvider(): String? {
        return try {
            listView.context.appWidgetManager.getAppWidgetInfo(widgetId)
                ?.provider?.flattenToString()
        } catch (_: Throwable) {
            null
        }
    }

    override fun shouldLayout(): Boolean {
        if (!listView.isAttachedToWindow) {
            listView.context.logUtils.debugLog(
                message = "${listView::class.java.name} ${safeViewIdName()} not attached to window so not laying out.\n" +
                        "Widget ID: $widgetId.\n" +
                        "Widget provider: ${findAppWidgetProvider()}.",
            )
            return false
        }

        listView.adapter?.let { adapter ->
            if (listView.count != adapter.count) {
                listView.context.logUtils.debugLog(
                    message = "Mismatch in listview count (${listView.count}) and adapter count (${adapter.count}) " +
                            "for ${listView::class.java.name} with ID name ${safeViewIdName()}.\n" +
                            "Widget ID: $widgetId.\n" +
                            "Widget provider: ${findAppWidgetProvider()}.",
                    throwable = null,
                )

                return false
            }
        }

        return true
    }
}
