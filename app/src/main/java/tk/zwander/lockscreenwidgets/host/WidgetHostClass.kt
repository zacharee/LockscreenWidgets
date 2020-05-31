package tk.zwander.lockscreenwidgets.host

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.SuperMethodCall

class WidgetHostClass(context: Context, id: Int, unlockCallback: (() -> Unit)?)
    : WidgetHostCompat(
    context, id, ByteBuddy()
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
                    .intercept(
                        MethodDelegation.to(InnerOnClickHandlerPie(unlockCallback))
                        .andThen(SuperMethodCall.INSTANCE)
                    )
            }
        }
        .make()
        .load(WidgetHostCompat::class.java.classLoader, AndroidClassLoadingStrategy.Wrapping(context.cacheDir))
        .loaded
        .newInstance()
) {
    class InnerOnClickHandlerPie(private val unlockCallback: (() -> Unit)?) {
        fun onClickHandler(
            view: View,
            pendingIntent: PendingIntent,
            fillInIntent: Intent
        ): Boolean {
            if (pendingIntent.isActivity) {
                unlockCallback?.invoke()
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
                unlockCallback?.invoke()
            }

            return true
        }
    }
}