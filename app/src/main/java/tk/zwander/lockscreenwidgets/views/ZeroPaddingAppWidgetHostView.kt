package tk.zwander.lockscreenwidgets.views

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import tk.zwander.lockscreenwidgets.util.dpAsPx

class ZeroPaddingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)

        val oneDp = context.dpAsPx(1)
        setPadding(oneDp, oneDp, oneDp, oneDp)
    }
}