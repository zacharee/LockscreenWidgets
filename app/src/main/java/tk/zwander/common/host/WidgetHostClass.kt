package tk.zwander.common.host

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.SuperMethodCall

/**
 * An implementation of [AppWidgetHost] used on devices where the hidden API object
 * [RemoteViews.OnClickHandler] is a class (i.e. Android Pie and below).
 * The handler is implemented through dynamic bytecode generation using ByteBuddy.
 * Since Lockscreen Widgets targets an API level above Pie, the [RemoteViews.OnClickHandler]
 * visible to it is an interface, so we can't just create a stub class.
 */
@SuppressLint("PrivateApi")
class WidgetHostClass(context: Context, id: Int, private val clickHandlerClass: Class<*>) : WidgetHostCompat(context, id) {
    override fun createOnClickHandlerForWidget(widgetId: Int): Any {
        return ByteBuddy()
            .subclass(clickHandlerClass)
            .name("OnClickHandlerPieIntercept")
            .defineMethod("onClickHandler", Boolean::class.java)
            .withParameters(View::class.java, PendingIntent::class.java, Intent::class.java)
            .intercept(
                MethodDelegation.to(InnerOnClickHandlerClass(widgetId))
                    .andThen(SuperMethodCall.INSTANCE)
            )
            .apply {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                    defineMethod("onClickHandler", Boolean::class.java)
                        .withParameters(View::class.java, PendingIntent::class.java, Intent::class.java, Int::class.java)
                        .intercept(
                            MethodDelegation.to(InnerOnClickHandlerClass(widgetId))
                                .andThen(SuperMethodCall.INSTANCE)
                        )
                }
            }
            .make()
            .load(WidgetHostCompat::class.java.classLoader, AndroidClassLoadingStrategy.Wrapping(context.cacheDir))
            .loaded
            .getDeclaredConstructor()
            .newInstance()
    }

    inner class InnerOnClickHandlerClass(private val widgetId: Int) : BaseInnerOnClickHandler() {
        @Suppress("UNUSED_PARAMETER", "unused")
        fun onClickHandler(
            view: View,
            pendingIntent: PendingIntent,
            fillInIntent: Intent
        ): Boolean {
            checkPendingIntent(pendingIntent, widgetId)

            return true
        }

        @Suppress("UNUSED_PARAMETER", "unused")
        fun onClickHandler(
            view: View,
            pendingIntent: PendingIntent,
            fillInIntent: Intent,
            windowingMode: Int
        ): Boolean {
            checkPendingIntent(pendingIntent, widgetId)

            return true
        }
    }
}