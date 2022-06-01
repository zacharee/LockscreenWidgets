package tk.zwander.lockscreenwidgets.host

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
 * An implementation of [AppWidgetHost] used on devices that don't have [RemoteViews.OnClickHandler]
 * (i.e., Android 12+). Instead, this uses [RemoteViews.InteractionHandler], which is just a renamed version
 * of [RemoteViews.OnClickHandler].
 */
@SuppressLint("PrivateApi")
class WidgetHost12(context: Context, id: Int, unlockCallback: ((Boolean) -> Unit)?) : WidgetHostCompat(
    context, id, Proxy.newProxyInstance(
        Class.forName("android.widget.RemoteViews\$InteractionHandler").classLoader,
        arrayOf(Class.forName("android.widget.RemoteViews\$InteractionHandler")),
        InnerOnClickHandler12(context, unlockCallback)
    )
) {
    class InnerOnClickHandler12(context: Context, unlockCallback: ((Boolean) -> Unit)?) : BaseInnerOnClickHandler(context, unlockCallback),
        InvocationHandler {
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>): Any {
            val view = args[0] as View
            val pi = args[1] as PendingIntent
            val response = args[2]

            val responseClass = Class.forName("android.widget.RemoteViews\$RemoteResponse")

            val getLaunchOptions = responseClass.getDeclaredMethod("getLaunchOptions", View::class.java)
            val startPendingIntent = RemoteViews::class.java.getDeclaredMethod(
                "startPendingIntent", View::class.java, PendingIntent::class.java, android.util.Pair::class.java)

            @Suppress("UNCHECKED_CAST")
            val launchOptions = getLaunchOptions.invoke(response, view) as android.util.Pair<Intent, ActivityOptions>

            checkPendingIntent(pi)

            return startPendingIntent.invoke(null, view, pi, launchOptions) as Boolean
        }
    }
}