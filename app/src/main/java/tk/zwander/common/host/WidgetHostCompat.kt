package tk.zwander.common.host

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.RemoteViews
import tk.zwander.common.util.safeApplicationContext
import tk.zwander.common.views.ZeroPaddingAppWidgetHostView
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

val Context.widgetHostCompat: WidgetHostCompat
    get() = WidgetHostCompat.getInstance(this)

/**
 * Base widget host class. [WidgetHostClass] and [WidgetHostInterface] extend this class and
 * are used conditionally, depending on whether [RemoteViews.OnClickHandler] is a class or interface
 * or [RemoteViews.InteractionHandler] is used on the device.
 *
 * @param context a Context object
 * @param id the ID of this widget host
 * implementation defined in the subclass
 */
abstract class WidgetHostCompat(
    val context: Context,
    id: Int,
) : AppWidgetHost(context, id) {
    companion object {
        private const val HOST_ID = 1003

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
        @Synchronized
        fun getInstance(context: Context): WidgetHostCompat {
            return instance ?: run {
                val onClickHandlerClass = ON_CLICK_HANDLER_CLASS
                val interactionHandlerClass = INTERACTION_HANDLER_CLASS

                val newInstance = when {
                    interactionHandlerClass != null && interactionHandlerClass.isInterface -> {
                        WidgetHostInterface(context.safeApplicationContext, HOST_ID, interactionHandlerClass)
                    }
                    onClickHandlerClass != null && onClickHandlerClass.isInterface -> {
                        WidgetHostInterface(context.safeApplicationContext, HOST_ID, onClickHandlerClass)
                    }
                    onClickHandlerClass != null && !onClickHandlerClass.isInterface -> {
                        WidgetHostClass(context.safeApplicationContext, HOST_ID, onClickHandlerClass)
                    }
                    else -> {
                        throw IllegalStateException("Unable to find correct click/interaction handler!\n" +
                                "Interaction Handler: ${interactionHandlerClass?.run { "$canonicalName / $isInterface" }}\n" +
                                "Click Handler: ${onClickHandlerClass?.run { "$canonicalName / $isInterface" }}")
                    }
                }

                instance = newInstance

                newInstance
            }
        }
    }

    protected abstract val onClickHandler: Any
    protected val onClickCallbacks = mutableSetOf<OnClickCallback>()

    private val listeningCount = AtomicInteger(0)
    private val listeners = ConcurrentLinkedQueue<Any>()

    fun addOnClickCallback(callback: OnClickCallback) {
        onClickCallbacks.add(callback)
    }

    fun removeOnClickCallback(callback: OnClickCallback) {
        onClickCallbacks.remove(callback)
    }

    fun startListening(listener: Any) {
        listeners.add(listener)
        startListening()
    }

    fun stopListening(listener: Any) {
        listeners.remove(listener)
        stopListening()
    }

    override fun stopListening() {
        if (listeners.isEmpty()) {
            super.stopListening()
        }
    }

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        AppWidgetHost::class.java
            .getDeclaredField(if (ON_CLICK_HANDLER_CLASS == null) "mInteractionHandler" else "mOnClickHandler")
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