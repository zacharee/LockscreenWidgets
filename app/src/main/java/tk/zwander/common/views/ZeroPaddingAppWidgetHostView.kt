package tk.zwander.common.views

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.RemoteViews
import androidx.core.view.updateLayoutParams
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.*
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
) : AppWidgetHostView(RemoteViewsLayoutInflaterContext(context = context, widgetId = widgetId)) {
    init {
        isNestedScrollingEnabled = false
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)

        context.appWidgetInfoCache[appWidgetId] = info

        setPadding(0, 0, 0, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        onAttach(this)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Keep the ID list widget from replacing its collection while the user is
        // interacting with any hosted widget.
        DebugIDsManager.notifyIdListInteraction()
        return super.dispatchTouchEvent(event)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams? {
        return try {
            super.generateLayoutParams(attrs)
        } catch (e: UnsupportedOperationException) {
            context.logUtils.debugLog("Error generating layout params for ${appWidgetInfo?.provider}", e)
            generateDefaultLayoutParams()
        }
    }

    override fun getDefaultView(): View? {
        return super.getDefaultView()?.also { view ->
            if (view !is AdapterView<*>) {
                view.setOnClickListener(::onDefaultViewClicked)
            }
        }
    }

    override fun onDefaultViewClicked(view: View) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.appWidgetManager.noteAppWidgetTapped(appWidgetId)
            }

            appWidgetInfo?.let { info ->
                context.packageManager.getLaunchIntentForPackage(info.provider.packageName)
                    ?.let { mainIntent ->
                        if (onDefaultClick(
                                PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE),
                            )
                        ) {
                            context.startActivity(mainIntent)
                        }
                    }
            }
        } catch (e: SecurityException) {
            context.logUtils.normalLog("Error launching default view click for ${appWidgetInfo?.provider}", e)
        }
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)

        context.logUtils.debugLog("Updating widget $appWidgetId, ${appWidgetInfo?.provider}, $remoteViews", null)

        val oldViews = context.widgetHostCompat.cachedRemoteViews[appWidgetId]

        if (oldViews != remoteViews) {
            context.widgetHostCompat.cachedRemoteViews[appWidgetId] = remoteViews

            findWidgetStackId()?.let {
                WidgetStackProvider.update(App.instance, intArrayOf(it), true)
            }
        }
    }

    override fun updateAppWidgetOptions(options: Bundle?) {
        super.updateAppWidgetOptions(options)

        context.logUtils.debugLog("Updating widget options $appWidgetId, $options", null)

        findWidgetStackId()?.let {
            WidgetStackProvider.updateOptions(App.instance, intArrayOf(it), options)
        }
    }

    override fun onViewDataChanged(viewId: Int) {
        super.onViewDataChanged(viewId)

        // This host view can be used to listen for updates to widgets that are embedded in a
        // stack. In that case, the view that's actually on screen is the stack's, and its
        // adapter is bound to RemoteViewsProxyService, so forward the notification to the
        // stack that shows this widget.
        val stacks = App.instance.prefManager.widgetStackWidgets
        if (stacks.isEmpty()) {
            return
        }

        val indices = App.instance.prefManager.widgetStackIndices

        stacks.entries.forEach { entry ->
            val stackId = entry.key

            if (entry.value.elementAtOrNull(indices[stackId] ?: 0)?.id == appWidgetId) {
                try {
                    // The updated API sets the entire RemoteViews, so this is for widgets using the older API.
                    @Suppress("DEPRECATION")
                    context.appWidgetManager.notifyAppWidgetViewDataChanged(stackId, viewId)
                } catch (e: Exception) {
                    context.logUtils.normalLog(
                        message = "Error notifying stack $stackId of view data change",
                        throwable = e,
                        extras = mapOf(
                            "stackId" to stackId,
                            "widgetId" to appWidgetId,
                            "viewId" to viewId,
                        ),
                    )
                }
            }
        }
    }

    override fun prepareView(view: View?) {
        super.prepareView(view)

        if (view?.layoutParams?.width == 0 || view?.layoutParams?.height == 0) {
            view.updateLayoutParams {
                width = LayoutParams.MATCH_PARENT
                height = LayoutParams.MATCH_PARENT
            }
        }
    }

    private fun findWidgetStackId(): Int? {
        return App.instance.prefManager.widgetStackWidgets.entries.firstOrNull { stack ->
            stack.value.any { it.id == appWidgetId }
        }?.key
    }
}
