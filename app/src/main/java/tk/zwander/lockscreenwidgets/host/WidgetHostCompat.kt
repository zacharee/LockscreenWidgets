package tk.zwander.lockscreenwidgets.host

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Looper
import android.widget.RemoteViews
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.views.MinPaddingAppWidgetHostView

/**
 * Base widget host class. [WidgetHostClass], [WidgetHostInterface], and [WidgetHost12] extend this class and
 * are used conditionally, depending on whether [RemoteViews.OnClickHandler] is a class or interface
 * or [RemoteViews.InteractionHandler] is used on the device.
 *
 * @param context a Context object
 * @param id the ID of this widget host
 * @param onClickHandler the [RemoteViews.OnClickHandler] or [RemoteViews.InteractionHandler]
 * implementation defined in the subclass
 */
abstract class WidgetHostCompat(
    val context: Context,
    id: Int,
    onClickHandler: Any
) : AppWidgetHost(context, id) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: WidgetHostCompat? = null

        fun getInstance(context: Context, id: Int, unlockCallback: (() -> Unit)? = null): WidgetHostCompat {
            return instance ?: run {
                if (!onClickHandlerExists) {
                    WidgetHost12(context.applicationContext, id, unlockCallback)
                } else {
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

        private val onClickHandlerExists: Boolean
            get() = try {
                Class.forName("android.widget.RemoteViews\$OnClickHandler")
                true
            } catch (e: ClassNotFoundException) {
                //Should crash if neither exists
                Class.forName("android.widget.RemoteViews\$InteractionHandler")
                false
            }
    }

    init {
        AppWidgetHost::class.java
            .getDeclaredField(if (!onClickHandlerExists) "mInteractionHandler" else "mOnClickHandler")
            .apply {
                isAccessible = true
                set(this@WidgetHostCompat, onClickHandler)
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