package tk.zwander.common.host

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * An implementation of [AppWidgetHost] used on devices where the hidden API object
 * [RemoteViews.OnClickHandler] is an interface (i.e. Android 10 and above). Java makes
 * it pretty easy to implement interfaces through reflection, so no ByteBuddy needed here.
 */
@SuppressLint("PrivateApi")
open class WidgetHostInterface(context: Context, id: Int, private val clickHandlerClass: Class<*>) : WidgetHostCompat(context, id) {
    override fun createOnClickHandlerForWidget(widgetId: Int): Any {
        return Proxy.newProxyInstance(
            clickHandlerClass.classLoader,
            arrayOf(clickHandlerClass),
            InnerOnClickHandlerInterface(widgetId)
        )
    }

    open inner class InnerOnClickHandlerInterface(private val widgetId: Int) : BaseInnerOnClickHandler(), InvocationHandler {
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any {
            val view = args?.getOrNull(0) as? View
            val pi = args?.getOrNull(1) as? PendingIntent
            val response = args?.getOrNull(2)

            val responseClass = Class.forName("android.widget.RemoteViews\$RemoteResponse")

            val getLaunchOptions = responseClass.getDeclaredMethod("getLaunchOptions", View::class.java)
            val startPendingIntent = RemoteViews::class.java.getDeclaredMethod(
                "startPendingIntent", View::class.java, PendingIntent::class.java, android.util.Pair::class.java)

            @Suppress("UNCHECKED_CAST")
            val launchOptions = getLaunchOptions.invoke(response, view) as android.util.Pair<Intent, ActivityOptions>

            checkPendingIntent(pi, widgetId)

            return startPendingIntent.invoke(null, view, pi, launchOptions) as Boolean
        }
    }
}