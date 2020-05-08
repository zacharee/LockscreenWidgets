package tk.zwander.lockscreenwidgets.views

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

class ZeroPaddingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)

        setPadding(0, 0, 0, 0)
    }
}