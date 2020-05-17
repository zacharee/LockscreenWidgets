package tk.zwander.lockscreenwidgets.host

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.SuperMethodCall
import tk.zwander.lockscreenwidgets.views.ZeroPaddingAppWidgetHostView
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class WidgetHost(context: Context, id: Int, unlockCallback: () -> Unit) : AppWidgetHost(
    context, id,
    if (RemoteViews.OnClickHandler::class.java.isInterface)
        Proxy.newProxyInstance(
            RemoteViews.OnClickHandler::class.java.classLoader,
            arrayOf(RemoteViews.OnClickHandler::class.java),
            InnerOnClickHandlerQ(unlockCallback)
        ) as RemoteViews.OnClickHandler
    else ByteBuddy()
        .subclass(RemoteViews.OnClickHandler::class.java)
        .name("OnClickHandlerPieIntercept")
        .defineMethod("onClickHandler", Boolean::class.java)
        .withParameters(View::class.java, PendingIntent::class.java, Intent::class.java)
        .intercept(
            MethodDelegation.to(InnerOnClickHandlerPie(unlockCallback))
            .andThen(SuperMethodCall.INSTANCE)
        )
        .apply {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                defineMethod("onClickHandler", Boolean::class.java)
                    .withParameters(View::class.java, PendingIntent::class.java, Intent::class.java, Int::class.java)
                    .intercept(MethodDelegation.to(InnerOnClickHandlerPie(unlockCallback))
                        .andThen(SuperMethodCall.INSTANCE)
                    )
            }
        }
        .make()
        .load(WidgetHost::class.java.classLoader, AndroidClassLoadingStrategy.Wrapping(context.cacheDir))
        .loaded
        .newInstance(),
    Looper.getMainLooper()
) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return ZeroPaddingAppWidgetHostView(context)
    }

    class InnerOnClickHandlerPie(private val unlockCallback: () -> Unit) {
        fun onClickHandler(
            view: View,
            pendingIntent: PendingIntent,
            fillInIntent: Intent
        ): Boolean {
            if (pendingIntent.isActivity) {
                unlockCallback()
            }

            return true
        }

        fun onClickHandler(
            view: View,
            pendingIntent: PendingIntent,
            fillInIntent: Intent,
            windowingMode: Int
        ): Boolean {
            if (pendingIntent.isActivity) {
                unlockCallback()
            }

            return true
        }
    }

    class InnerOnClickHandlerQ(private val unlockCallback: () -> Unit) : InvocationHandler {
        @SuppressLint("BlockedPrivateApi", "PrivateApi")
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>): Any {
            val view = args[0] as View
            val pi = args[1] as PendingIntent
            val response = args[2]

            val responseClass = Class.forName("android.widget.RemoteViews\$RemoteResponse")

            val getLaunchOptions = responseClass.getDeclaredMethod("getLaunchOptions", View::class.java)
            val startPendingIntent = RemoteViews::class.java.getDeclaredMethod(
                "startPendingIntent", View::class.java, PendingIntent::class.java, android.util.Pair::class.java)

            val launchOptions = getLaunchOptions.invoke(response, view) as android.util.Pair<Intent, ActivityOptions>

            if (pi.isActivity) {
                unlockCallback()
            }

            return startPendingIntent.invoke(null, view, pi, launchOptions) as Boolean
        }
    }
}