package tk.zwander.common.views

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * An implementation of [AppWidgetHostView] that decreases the default widget padding
 * to 0dp.
 */
class ZeroPaddingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)

        setPadding(0, 0, 0, 0)
    }
}