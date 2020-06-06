package tk.zwander.lockscreenwidgets.views

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import tk.zwander.lockscreenwidgets.util.dpAsPx

/**
 * An implementation of [AppWidgetHostView] that decreases the default widget padding
 * to 1dp.
 *
 * We use 1dp instead of 0 because of a potential half-pixel issue. If the width of the frame
 * is an odd number of pixels, and the number of columns per page is even, a sliver of the
 * next/previous page of widgets may be shown in the frame. This prevents that.
 */
class MinPaddingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)

        val oneDp = context.dpAsPx(1)
        setPadding(oneDp, oneDp, oneDp, oneDp)
    }
}