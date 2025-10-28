@file:Suppress("KDocUnresolvedReference")

package tk.zwander.common.host

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.BadParcelableException
import android.os.Build
import android.os.DeadObjectException
import android.view.View
import android.widget.RemoteViews
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import tk.zwander.common.compose.util.widgetViewCacheRegistry
import tk.zwander.common.util.logUtils
import tk.zwander.common.views.ZeroPaddingAppWidgetHostView
import tk.zwander.lockscreenwidgets.util.IconPrefs
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedQueue

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
class WidgetHostCompat(
    private val context: Context,
    val mode: Mode,
    id: Int,
) : AppWidgetHost(context, id) {
    companion object {
        private const val HOST_ID = 1003

        private val ON_CLICK_HANDLER_CLASS by lazy {
            try {
                @SuppressLint("PrivateApi")
                Class.forName($$"android.widget.RemoteViews$OnClickHandler")
            } catch (_: ClassNotFoundException) {
                null
            }
        }

        private val INTERACTION_HANDLER_CLASS by lazy {
            try {
                @SuppressLint("PrivateApi")
                Class.forName($$"android.widget.RemoteViews$InteractionHandler")
            } catch (_: ClassNotFoundException) {
                null
            }
        }

        @SuppressLint("StaticFieldLeak")
        private var instance: WidgetHostCompat? = null

        @SuppressLint("PrivateApi")
        @Synchronized
        fun getInstance(context: Context): WidgetHostCompat {
            return instance ?: run {
                val mode = when {
                    INTERACTION_HANDLER_CLASS != null && INTERACTION_HANDLER_CLASS!!.isInterface -> Mode.Interface(INTERACTION_HANDLER_CLASS!!)
                    ON_CLICK_HANDLER_CLASS != null && ON_CLICK_HANDLER_CLASS!!.isInterface -> Mode.Interface(ON_CLICK_HANDLER_CLASS!!)
                    ON_CLICK_HANDLER_CLASS != null && !ON_CLICK_HANDLER_CLASS!!.isInterface -> Mode.Class(ON_CLICK_HANDLER_CLASS!!)
                    else -> {
                        throw IllegalStateException("Unable to find correct click/interaction handler!\n" +
                                "Interaction Handler: ${INTERACTION_HANDLER_CLASS?.run { "$canonicalName / $isInterface" }}\n" +
                                "Click Handler: ${ON_CLICK_HANDLER_CLASS?.run { "$canonicalName / $isInterface" }}",
                        )
                    }
                }

                WidgetHostCompat(context, mode, HOST_ID).also {
                    instance = it
                }
            }
        }
    }

    private val onClickCallbacks = mutableSetOf<OnClickCallback>()
    private val listeners = ConcurrentLinkedQueue<Any>()

    private fun createOnClickHandlerForWidget(widgetId: Int): Any {
        return when (mode) {
            is Mode.Class -> {
                val clickHandler = BaseInnerOnClickHandler.InnerOnClickHandlerClass(
                    context = context,
                    widgetId = widgetId,
                    onClickCallbacks = onClickCallbacks,
                    clickHandlerClass = mode.handlerClass,
                )

                ByteBuddy()
                    .subclass(mode.handlerClass)
                    .name("android.widget.remoteviews.OnClickHandlerPieIntercept")
                    .defineMethod("onClickHandler", Boolean::class.java)
                    .withParameters(View::class.java, PendingIntent::class.java, Intent::class.java)
                    .intercept(MethodDelegation.to(clickHandler))
                    .apply {
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                            defineMethod("onClickHandler", Boolean::class.java)
                                .withParameters(View::class.java, PendingIntent::class.java, Intent::class.java, Int::class.java)
                                .intercept(MethodDelegation.to(clickHandler))
                        }
                    }
                    .make()
                    .load(WidgetHostCompat::class.java.classLoader, AndroidClassLoadingStrategy.Wrapping(context.cacheDir))
                    .loaded
                    .getDeclaredConstructor()
                    .newInstance()
            }
            is Mode.Interface -> {
                Proxy.newProxyInstance(
                    mode.handlerClass.classLoader,
                    arrayOf(mode.handlerClass),
                    BaseInnerOnClickHandler.InnerOnClickHandlerInterface(context, widgetId, onClickCallbacks),
                )
            }
        }
    }

    fun addOnClickCallback(callback: OnClickCallback) {
        onClickCallbacks.add(callback)
    }

    fun removeOnClickCallback(callback: OnClickCallback) {
        onClickCallbacks.remove(callback)
    }

    fun startListening(listener: Any) {
        listeners.add(listener)
        try {
            startListening()
        } catch (_: BadParcelableException) {
            // It's possible for retrieving pending updates to fail, causing
            // a crash. There doesn't seem to be a way to fix this, but catching
            // the error here should at least allow future updates to be received.
        } catch (_: DeadObjectException) {
            // Same as above.
        } catch (_: RuntimeException) {
            // Same as above.
        }
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

    override fun deleteAppWidgetId(appWidgetId: Int) {
        super.deleteAppWidgetId(appWidgetId)

        IconPrefs.removeIcon(context, appWidgetId)
        context.widgetViewCacheRegistry.removeView(appWidgetId)
    }

    @SuppressLint("PrivateApi")
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView {
        val clickHandler = createOnClickHandlerForWidget(appWidgetId)

        AppWidgetHost::class.java
            .getDeclaredField(if (ON_CLICK_HANDLER_CLASS == null) "mInteractionHandler" else "mOnClickHandler")
            .apply {
                isAccessible = true
                set(this@WidgetHostCompat, clickHandler)
            }

        return ZeroPaddingAppWidgetHostView(context) { hostView ->
            try {
                AppWidgetHostView::class.java
                    .getMethod(
                        if (INTERACTION_HANDLER_CLASS != null) {
                            "setInteractionHandler"
                        } else {
                            "setOnClickHandler"
                        },
                        INTERACTION_HANDLER_CLASS ?: ON_CLICK_HANDLER_CLASS,
                    )
                    .invoke(hostView, clickHandler)
            } catch (e: Throwable) {
                context.logUtils.normalLog("Unable to update interaction handler on window attach for widget $appWidgetId, ${appWidget?.provider}.", e)
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    sealed class BaseInnerOnClickHandler(
        protected val context: Context,
        protected val onClickCallbacks: MutableSet<OnClickCallback>,
        protected val widgetId: Int,
    ) {
        class InnerOnClickHandlerInterface(
            context: Context,
            widgetId: Int,
            onClickCallbacks: MutableSet<OnClickCallback>,
        ) : BaseInnerOnClickHandler(context, onClickCallbacks, widgetId), InvocationHandler {
            @SuppressLint("BlockedPrivateApi", "PrivateApi")
            override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                if (method?.name == "onScroll") return null

                val view = args?.getOrNull(0) as? View
                val pi = args?.getOrNull(1) as? PendingIntent
                val response = args?.getOrNull(2)

                val responseClass = Class.forName($$"android.widget.RemoteViews$RemoteResponse")

                val getLaunchOptions = responseClass.getDeclaredMethod("getLaunchOptions", View::class.java)
                val startPendingIntent = RemoteViews::class.java.getDeclaredMethod(
                    "startPendingIntent", View::class.java, PendingIntent::class.java, android.util.Pair::class.java)

                @Suppress("UNCHECKED_CAST")
                val launchOptions = response?.let {
                    getLaunchOptions.invoke(response, view) as? android.util.Pair<Intent, ActivityOptions>
                }

                return if (checkPendingIntent(pi, widgetId)) {
                    startPendingIntent.invoke(null, view, pi, launchOptions) as Boolean
                } else {
                    false
                }
            }
        }

        class InnerOnClickHandlerClass(
            context: Context,
            widgetId: Int,
            onClickCallbacks: MutableSet<OnClickCallback>,
            private val clickHandlerClass: Class<*>,
        ) : BaseInnerOnClickHandler(context, onClickCallbacks, widgetId) {
            private val defaultHandler = clickHandlerClass.getConstructor().newInstance()

            @Suppress("unused")
            fun onClickHandler(
                view: View,
                pendingIntent: PendingIntent,
                fillInIntent: Intent
            ): Boolean {
                return if (checkPendingIntent(pendingIntent, widgetId)) {
                    clickHandlerClass.getMethod("onClickHandler", View::class.java, PendingIntent::class.java, Intent::class.java)
                        .invoke(defaultHandler, view, pendingIntent, fillInIntent) as Boolean
                } else {
                    false
                }
            }

            @Suppress("unused")
            fun onClickHandler(
                view: View,
                pendingIntent: PendingIntent,
                fillInIntent: Intent,
                windowingMode: Int
            ): Boolean {
                return if (checkPendingIntent(pendingIntent, widgetId)) {
                    clickHandlerClass.getMethod("onClickHandler", View::class.java, PendingIntent::class.java, Intent::class.java, Int::class.java)
                        .invoke(defaultHandler, view, pendingIntent, fillInIntent, windowingMode) as Boolean
                } else {
                    false
                }
            }
        }

        @SuppressLint("NewApi")
        fun checkPendingIntent(pendingIntent: PendingIntent?, widgetId: Int): Boolean {
            context.logUtils.debugLog(
                "Intercepting PendingIntent. " +
                        "isActivity: ${pendingIntent?.isActivity}. " +
                        "creatorPackage: ${pendingIntent?.creatorPackage}",
            )

            return if (pendingIntent != null) {
                val triggerUnlockOrDismiss = pendingIntent.isActivity
                // This package check is so the frame/drawer doesn't dismiss itself when the
                // Open Drawer widget is tapped.
                if (pendingIntent.creatorPackage != context.packageName) {
                    onClickCallbacks.all { callback ->
                        if (callback.hasWidgetId(widgetId)) {
                            callback.onWidgetClick(triggerUnlockOrDismiss)
                        } else {
                            true
                        }
                    }
                } else {
                    true
                }
            } else {
                true
            }
        }
    }

    interface OnClickCallback {
        fun hasWidgetId(id: Int): Boolean
        fun onWidgetClick(trigger: Boolean): Boolean
    }

    sealed class Mode(val handlerClass: java.lang.Class<*>) {
        class Class(handlerClass: java.lang.Class<*>) : Mode(handlerClass)
        class Interface(handlerClass: java.lang.Class<*>) : Mode(handlerClass)
    }
}