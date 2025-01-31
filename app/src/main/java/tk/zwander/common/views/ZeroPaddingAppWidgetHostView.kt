package tk.zwander.common.views

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.view.View
import tk.zwander.common.util.logUtils

/**
 * An implementation of [AppWidgetHostView] that decreases the default widget padding
 * to 0dp.
 */
class ZeroPaddingAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)

        setPadding(0, 0, 0, 0)
    }

    override fun dispatchDraw(canvas: Canvas) {
        context.logUtils.debugLog("dispatchDraw() AppWidgetHostView for ${appWidgetInfo?.provider}", null)
        super.dispatchDraw(canvas)
    }

    override fun draw(canvas: Canvas) {
        context.logUtils.debugLog("draw() AppWidgetHostView for ${appWidgetInfo?.provider}", null)
        super.draw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
        context.logUtils.debugLog("drawChild() AppWidgetHostView for ${appWidgetInfo?.provider}", null)
        return super.drawChild(canvas, child, drawingTime)
    }
}