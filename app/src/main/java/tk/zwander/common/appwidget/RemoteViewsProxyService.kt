package tk.zwander.common.appwidget

import android.annotation.SuppressLint
import android.app.IServiceConnection
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.Keep
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.android.internal.widget.IRemoteViewsFactory
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers.named
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.mainHandler

class RemoteViewsProxyService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory? {
        return null
    }

    override fun onBind(intent: Intent?): IBinder? {
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it != -1 } ?: return null
        val widgetIntent = IntentCompat.getParcelableExtra(intent, EXTRA_INTENT, Intent::class.java)
            ?: return null

        sFactories[widgetId]?.let {
            if (!it.created) {
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
        var created = false
        var wrapped: IRemoteViewsFactory? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                wrapped = asInterface(service)
                created = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                wrapped = null
                created = false
                try {
                    unbindService(this)
                } catch (_: IllegalArgumentException) {}
            }

            override fun onNullBinding(name: ComponentName?) {
                wrapped = null
                created = false
                try {
                    unbindService(this)
                } catch (_: IllegalArgumentException) {}
            }
        }

        @SuppressLint("PrivateApi")
        fun onCreate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appWidgetManager.bindRemoteViewsService(
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
        }

        override fun onDataSetChanged() {
            if (!created || wrapped?.isCreated == false) {
                wrapped?.onDataSetChanged()
            }
        }

        override fun getCount(): Int {
            return wrapped?.count ?: 0
        }

        override fun getViewAt(position: Int): RemoteViews? {
            return wrapped?.getViewAt(position)
        }

        override fun getLoadingView(): RemoteViews? {
            return wrapped?.loadingView
        }

        override fun getViewTypeCount(): Int {
            return wrapped?.viewTypeCount ?: 1
        }

        override fun getItemId(position: Int): Long {
            return wrapped?.getItemId(position) ?: 0L
        }

        override fun hasStableIds(): Boolean {
            return wrapped?.hasStableIds() == true
        }

        override fun isCreated(): Boolean {
            return created
        }

        override fun getRemoteCollectionItems(
            capSize: Int,
            capBitmapSize: Int,
        ): RemoteViews.RemoteCollectionItems? {
            return wrapped?.getRemoteCollectionItems(capSize, capBitmapSize)
        }

        override fun onDestroy(intent: Intent?) {
            try {
                unbindService(connection)
            } catch (_: IllegalArgumentException) {}
            sFactories.remove(widgetId)
        }

        override fun onDataSetChangedAsync() {
            wrapped?.onDataSetChangedAsync()
        }
    }

    override fun onDestroy() {
        sFactories.forEach { (_, factory) ->
            try {
                unbindService(factory.connection)
            } catch (_: IllegalArgumentException) {}
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INTENT = "intent"

        private val sFactories = hashMapOf<Int, Factory>()

        fun createProxyIntent(context: Context, widgetId: Int, widgetIntent: Intent?): Intent {
            val intent = Intent(context, RemoteViewsProxyService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            intent.putExtra(EXTRA_INTENT, widgetIntent)
            intent.data = "widgetproxy://${widgetId}".toUri()

            return intent
        }
    }
}
