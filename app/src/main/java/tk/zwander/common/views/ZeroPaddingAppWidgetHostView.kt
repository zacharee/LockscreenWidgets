package tk.zwander.common.views

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.view.View
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.logUtils

/**
 * An implementation of [AppWidgetHostView] that decreases the default widget padding
 * to 0dp.
 */
@SuppressLint("ViewConstructor")
class ZeroPaddingAppWidgetHostView(
    context: Context,
    private val onAttach: (AppWidgetHostView) -> Unit,
    private val onDefaultClick: (PendingIntent) -> Boolean,
) : AppWidgetHostView(context) {
    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)

        setPadding(0, 0, 0, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        onAttach(this)
    }

    override fun dispatchDraw(canvas: Canvas) {
        context.logUtils.debugLog(
            "dispatchDraw() AppWidgetHostView for ${appWidgetInfo?.provider}",
            null
        )
        super.dispatchDraw(canvas)
    }

    override fun draw(canvas: Canvas) {
        context.logUtils.debugLog("draw() AppWidgetHostView for ${appWidgetInfo?.provider}", null)
        super.draw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
        context.logUtils.debugLog(
            "drawChild() AppWidgetHostView for ${appWidgetInfo?.provider}",
            null
        )
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun canHaveDisplayList(): Boolean {
        context.logUtils.debugLog("canHaveDisplayList() ${this::class.java.name}")
        return super.canHaveDisplayList()
    }

    override fun onDefaultViewClicked(view: View) {
        context.appWidgetManager.noteAppWidgetTapped(appWidgetId)

        appWidgetInfo?.let { info ->
            context.packageManager.getLaunchIntentForPackage(info.provider.packageName)
                ?.let { mainIntent ->
                    if (onDefaultClick(PendingIntent.getActivity(context, 0, mainIntent, 0))) {
                        context.startActivity(mainIntent)
                    }
                }
        }
    }
}
