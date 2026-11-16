package tk.zwander.common.appwidget

import android.annotation.SuppressLint
import android.app.IServiceConnection
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.android.internal.widget.IRemoteViewsFactory
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers.named
import tk.zwander.common.host.WidgetHostCompat
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler

class RemoteViewsProxyService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory? {
        return null
    }

    override fun onBind(intent: Intent?): IBinder? {
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WidgetHostCompat.INVALID_WIDGET_ID)
            ?.takeIf { it != WidgetHostCompat.INVALID_WIDGET_ID } ?: return null
        val widgetIntent = IntentCompat.getParcelableExtra(intent, EXTRA_INTENT, Intent::class.java)
            ?: return null

        sFactories[widgetId]?.let {
            // A new adapter is binding, so it needs to load the data itself. Without this a
            // reused factory reports itself as created and RemoteViewsAdapter skips
            // onDataSetChanged(), leaving the new adapter with whatever was cached before.
            it.dataLoaded = false

            if (it.wrapped?.asBinder()?.isBinderAlive != true) {
                it.created = false
                it.onCreate()
            }
            return it
        }

        val newFactory = Factory(widgetId, widgetIntent)
        newFactory.onCreate()

        sFactories[widgetId] = newFactory

        return newFactory
    }

    inner class Factory(
        private val widgetId: Int,
        private val widgetIntent: Intent,
    ) : IRemoteViewsFactory.Stub() {
        @Volatile
        var created = false

        /**
         * Whether this factory has forwarded a data load for the adapter that's currently bound
         * to it. Reset in [onBind] so every newly bound adapter reloads the data.
         */
        @Volatile
        var dataLoaded = false

        @Volatile
        private var binding = false

        @Volatile
        private var bindAttemptTime = 0L

        @Volatile
        private var pendingDataSetChanged = false

        @Volatile
        var wrapped: IRemoteViewsFactory? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                wrapped = asInterface(service)
                created = true
                binding = false

                // A data load was requested before the underlying factory finished binding.
                if (pendingDataSetChanged) {
                    pendingDataSetChanged = false

                    try {
                        wrapped?.onDataSetChanged()
                        dataLoaded = true
                    } catch (e: Exception) {
                        logUtils.debugLog("Error loading widget data $widgetId", e)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                wrapped = null
                created = false
                binding = false
                pendingDataSetChanged = false
                try {
                    unbindService(this)
                } catch (_: IllegalArgumentException) {
                }
            }

            override fun onNullBinding(name: ComponentName?) {
                wrapped = null
                created = false
                binding = false
                pendingDataSetChanged = false
                try {
                    unbindService(this)
                } catch (_: IllegalArgumentException) {
                }
            }
        }

        @SuppressLint("PrivateApi")
        fun onCreate() {
            val now = SystemClock.elapsedRealtime()

            // A bind is in flight. Retry eventually in case the connection callback never comes.
            if (binding && now - bindAttemptTime < BIND_RETRY_TIMEOUT) {
                return
            }

            val existing = wrapped
            if (existing != null && existing.asBinder()?.isBinderAlive == true) {
                created = true
                return
            }

            binding = true
            bindAttemptTime = now

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val bound = appWidgetManager.bindRemoteViewsService(
                        this@RemoteViewsProxyService,
                        widgetId,
                        widgetIntent,
                        try {
                            getServiceDispatcher(
                                connection,
                                mainHandler,
                                0,
                            )
                        } catch (_: NoSuchMethodError) {
                            Context::class.java.getMethod(
                                "getServiceDispatcher",
                                ServiceConnection::class.java,
                                Handler::class.java,
                                Int::class.java,
                            ).invoke(
                                this@RemoteViewsProxyService,
                                connection,
                                mainHandler,
                                0,
                            ) as IServiceConnection
                        },
                        BIND_AUTO_CREATE or BIND_FOREGROUND_SERVICE_WHILE_AWAKE,
                    )

                    if (!bound) {
                        binding = false
                    }
                } else {
                    val proxyClass = Class.forName(
                        $$"com.android.internal.widget.IRemoteViewsAdapterConnection$Stub",
                    )
                    val handler = object {
                        @Keep
                        fun onServiceConnected(service: IBinder?) {
                            connection.onServiceConnected(
                                ComponentName(this@RemoteViewsProxyService, Factory::class.java),
                                service,
                            )
                        }

                        @Keep
                        fun onServiceDisconnected() {
                            connection.onServiceDisconnected(
                                ComponentName(this@RemoteViewsProxyService, Factory::class.java),
                            )
                        }
                    }
                    val proxy = ByteBuddy().subclass(proxyClass)
                        .name("com.android.internal.widget.RemoteViewsAdapterConnectionProxy")
                        .method(named("onServiceConnected"))
                        .intercept(MethodDelegation.to(handler))
                        .method(named("onServiceDisconnected"))
                        .intercept(MethodDelegation.to(handler))
                        .make()
                        .load(Factory::class.java.classLoader, AndroidClassLoadingStrategy.Wrapping(cacheDir))
                        .loaded
                        .getDeclaredConstructor()
                        .newInstance() as IInterface

                    AppWidgetManager::class.java
                        .getDeclaredMethod(
                            "bindRemoteViewsService",
                            String::class.java,
                            Int::class.java,
                            Intent::class.java,
                            IBinder::class.java,
                        )
                        .invoke(
                            appWidgetManager,
                            packageName,
                            widgetId,
                            widgetIntent,
                            proxy.asBinder(),
                        )
                }
            } catch (e: Exception) {
                binding = false
                logUtils.debugLog("Error binding widget service $widgetId, $widgetIntent", e)
            }
        }

        override fun onDataSetChanged() {
            onCreate()

            val factory = wrapped
            if (factory == null) {
                pendingDataSetChanged = true
                return
            }

            factory.onDataSetChanged()
            dataLoaded = true
        }

        override fun getCount(): Int {
            onCreate()
            return wrapped?.count ?: 0
        }

        override fun getViewAt(position: Int): RemoteViews? {
            onCreate()
            return wrapped?.getViewAt(position)
        }

        override fun getLoadingView(): RemoteViews? {
            onCreate()
            return wrapped?.loadingView
        }

        override fun getViewTypeCount(): Int {
            onCreate()
            return wrapped?.viewTypeCount ?: 1
        }

        override fun getItemId(position: Int): Long {
            onCreate()
            return wrapped?.getItemId(position) ?: 0L
        }

        override fun hasStableIds(): Boolean {
            onCreate()
            return wrapped?.hasStableIds() == true
        }

        override fun isCreated(): Boolean {
            // Only report created once the data for the currently bound adapter has actually been
            // loaded. RemoteViewsAdapter only calls onDataSetChanged() when this is false.
            return created && dataLoaded
        }

        override fun getRemoteCollectionItems(
            p0: Int,
            p1: Int,
            p2: Boolean,
        ): RemoteViews.RemoteCollectionItems? {
            onCreate()
            return wrapped?.getRemoteCollectionItems(p0, p1, p2)
        }

        @Suppress("unused")
        @RequiresApi(Build.VERSION_CODES.S)
        fun getRemoteCollectionItems(
            capSize: Int,
            capBitmapSize: Int,
        ): RemoteViews.RemoteCollectionItems? {
            onCreate()

            return wrapped?.let {
                it::class.java.getDeclaredMethod(
                    "getRemoteCollectionItems",
                    Int::class.java,
                    Int::class.java,
                ).invoke(it, capSize, capBitmapSize) as?
                        RemoteViews.RemoteCollectionItems
            }
        }

        override fun onDestroy(intent: Intent?) {
            try {
                unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
            sFactories.remove(widgetId)
        }

        override fun onDataSetChangedAsync() {
            onCreate()

            val factory = wrapped ?: return

            factory.onDataSetChangedAsync()
            dataLoaded = true
        }
    }

    override fun onDestroy() {
        sFactories.forEach { [_, factory] ->
            try {
                unbindService(factory.connection)
            } catch (_: IllegalArgumentException) {
            }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INTENT = "intent"

        private const val BIND_RETRY_TIMEOUT = 5_000L

        private val sFactories = hashMapOf<Int, Factory>()

        fun createProxyIntent(context: Context, widgetId: Int, widgetIntent: Intent?): Intent {
            val intent = Intent(context, RemoteViewsProxyService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            intent.putExtra(EXTRA_INTENT, widgetIntent)
            intent.data = "widgetproxy://${widgetId}".toUri()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                intent.collectExtraIntentKeys()
            }

            return intent
        }
    }
}
