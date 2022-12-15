package tk.zwander.lockscreenwidgets.host

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.RemoteViews
import tk.zwander.lockscreenwidgets.util.safeApplicationContext
import tk.zwander.lockscreenwidgets.views.ZeroPaddingAppWidgetHostView

val Context.widgetHostCompat: WidgetHostCompat
    get() = WidgetHostCompat.getInstance(this)

/**
 * Base widget host class. [WidgetHostClass] and [WidgetHostInterface] extend this class and
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
) : AppWidgetHost(context, id) {
    companion object {
        const val HOST_ID = 1003
        @SuppressLint("PrivateApi")
        val ON_CLICK_HANDLER_CLASS = try {
            Class.forName("android.widget.RemoteViews\$OnClickHandler")
        } catch (e: ClassNotFoundException) {
            null
        }
        @SuppressLint("PrivateApi")
        val INTERACTION_HANDLER_CLASS = try {
            Class.forName("android.widget.RemoteViews\$InteractionHandler")
        } catch (e: ClassNotFoundException) {
            null
        }

        @SuppressLint("StaticFieldLeak")
        private var instance: WidgetHostCompat? = null

        @SuppressLint("PrivateApi")
        fun getInstance(context: Context): WidgetHostCompat {
            return instance ?: run {
                if (ON_CLICK_HANDLER_CLASS == null && INTERACTION_HANDLER_CLASS == null) {
                    throw IllegalStateException("Neither OnClickHandler nor InteractionHandler could be found!")
                }

                if (!onClickHandlerExists) {
                    WidgetHostInterface(context.safeApplicationContext, HOST_ID, INTERACTION_HANDLER_CLASS!!)
                } else {
                    (if (ON_CLICK_HANDLER_CLASS!!.isInterface) {
                        WidgetHostInterface(context.safeApplicationContext, HOST_ID, ON_CLICK_HANDLER_CLASS)
                    } else {
                        WidgetHostClass(context.safeApplicationContext, HOST_ID)
                    }).also {
                        instance = it
                    }
                }
            }
        }

        private val onClickHandlerExists: Boolean = ON_CLICK_HANDLER_CLASS != null
    }

    protected abstract val onClickHandler: Any
    protected val onClickCallbacks = mutableSetOf<OnClickCallback>()

    fun addOnClickCallback(callback: OnClickCallback) {
        onClickCallbacks.add(callback)
    }

    fun removeOnClickCallback(callback: OnClickCallback) {
        onClickCallbacks.remove(callback)
    }

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        AppWidgetHost::class.java
            .getDeclaredField(if (!onClickHandlerExists) "mInteractionHandler" else "mOnClickHandler")
            .apply {
                isAccessible = true
                set(this@WidgetHostCompat, onClickHandler)
            }

        return ZeroPaddingAppWidgetHostView(context)
    }

    abstract inner class BaseInnerOnClickHandler {
        @SuppressLint("NewApi")
        fun checkPendingIntent(pendingIntent: PendingIntent) {
            val triggerUnlockOrDismiss = pendingIntent.isActivity

            onClickCallbacks.forEach { it.onWidgetClick(triggerUnlockOrDismiss) }
        }
    }

    interface OnClickCallback {
        fun onWidgetClick(trigger: Boolean)
    }
}