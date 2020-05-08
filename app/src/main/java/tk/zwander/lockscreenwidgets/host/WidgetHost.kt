package tk.zwander.lockscreenwidgets.host

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import tk.zwander.lockscreenwidgets.views.ZeroPaddingAppWidgetHostView

class WidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return ZeroPaddingAppWidgetHostView(context)
    }
}