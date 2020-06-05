package tk.zwander.lockscreenwidgets.host

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Looper
import android.widget.RemoteViews
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
    context: Context,
    id: Int,
    onClickHandler: RemoteViews.OnClickHandler
) : AppWidgetHost(context, id, onClickHandler, Looper.getMainLooper()) {
    companion object {
        private var instance: WidgetHostCompat? = null

        fun getInstance(context: Context, id: Int, unlockCallback: (() -> Unit)? = null): WidgetHostCompat {
            return instance ?: run {
                (if (RemoteViews.OnClickHandler::class.java.isInterface) {
                    WidgetHostInterface(context, id, unlockCallback)
                } else {
                    WidgetHostClass(context, id, unlockCallback)
                }).also {
                    instance = it
                }
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
}