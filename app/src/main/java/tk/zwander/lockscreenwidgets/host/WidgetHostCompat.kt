package tk.zwander.lockscreenwidgets.host

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Looper
import android.widget.RemoteViews
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.views.MinPaddingAppWidgetHostView

/**
 * Base widget host class. [WidgetHostClass] and [WidgetHostInterface] extend this class and
 * are used conditionally, depending on whether [RemoteViews.OnClickHandler] is a class or interface
 * on the current device.
 *
 * @param context a Context object
 * @param id the ID of this widget host
 * @param onClickHandler the [RemoteViews.OnClickHandler] implementation defined in the subclass
 */
abstract class WidgetHostCompat(
    val context: Context,
    id: Int,
    onClickHandler: RemoteViews.OnClickHandler
) : AppWidgetHost(context, id, onClickHandler, Looper.getMainLooper()) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: WidgetHostCompat? = null

        fun getInstance(context: Context, id: Int, unlockCallback: (() -> Unit)? = null): WidgetHostCompat {
            return instance ?: run {
                (if (RemoteViews.OnClickHandler::class.java.isInterface) {
                    WidgetHostInterface(context.applicationContext, id, unlockCallback)
                } else {
                    WidgetHostClass(context.applicationContext, id, unlockCallback)
                }).also {
                    instance = it
                }
            }
        }
    }

    open class BaseInnerOnClickHandler(internal val context: Context, private val unlockCallback: (() -> Unit)?) {
        fun checkPendingIntent(pendingIntent: PendingIntent) {
            if (pendingIntent.isActivity && context.prefManager.requestUnlock) {
                unlockCallback?.invoke()
            }
        }
    }

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return MinPaddingAppWidgetHostView(context)
    }

    override fun deleteAppWidgetId(appWidgetId: Int) {
        //If a widget is deleted, we want to make sure any later widget that
        //happens to have the same ID doesn't inherit any custom sizing
        //applied to the widget being deleted.
        context.prefManager.run {
            widgetSizes = widgetSizes.apply { remove(appWidgetId) }
        }
        super.deleteAppWidgetId(appWidgetId)
    }
}