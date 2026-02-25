package tk.zwander.common.views

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.RemoteViews
import tk.zwander.common.util.RemoteViewsLayoutInflaterContext
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider

/**
 * An implementation of [AppWidgetHostView] that decreases the default widget padding
 * to 0dp.
 */
@SuppressLint("ViewConstructor")
class ZeroPaddingAppWidgetHostView(
    context: Context,
    widgetId: Int,
    private val onAttach: (AppWidgetHostView) -> Unit,
    private val onDefaultClick: (PendingIntent) -> Boolean,
) : AppWidgetHostView(RemoteViewsLayoutInflaterContext(context, widgetId)) {
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

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams? {
        return try {
            super.generateLayoutParams(attrs)
        } catch (e: UnsupportedOperationException) {
            context.logUtils.debugLog("Error generating layout params for ${appWidgetInfo?.provider}", e)
            generateDefaultLayoutParams()
        }
    }

    override fun onDefaultViewClicked(view: View) {
        context.appWidgetManager.noteAppWidgetTapped(appWidgetId)

        appWidgetInfo?.let { info ->
            context.packageManager.getLaunchIntentForPackage(info.provider.packageName)
                ?.let { mainIntent ->
                    if (onDefaultClick(
                            PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE),
                    )) {
                        context.startActivity(mainIntent)
                    }
                }
        }
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)

        findWidgetStackId()?.let {
            WidgetStackProvider.update(App.instance, intArrayOf(it), true)
        }
    }

    override fun updateAppWidgetOptions(options: Bundle?) {
        super.updateAppWidgetOptions(options)

        findWidgetStackId()?.let {
            WidgetStackProvider.updateOptions(App.instance, intArrayOf(it), options)
        }
    }

    private fun findWidgetStackId(): Int? {
        return App.instance.prefManager.widgetStackWidgets.entries.firstOrNull { stack ->
            stack.value.any { it.id == appWidgetId }
        }?.key
    }
}
