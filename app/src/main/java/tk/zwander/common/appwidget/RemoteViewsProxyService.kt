package tk.zwander.common.appwidget

import android.app.IServiceConnection
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.IntentCompat
import com.android.internal.widget.IRemoteViewsFactory
import tk.zwander.common.util.appWidgetManager

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
            return it
        }

        val newFactory = object : IRemoteViewsFactory.Stub() {
            var wrapped: IRemoteViewsFactory? = null

            init {
                appWidgetManager.bindRemoteViewsService(
                    this@RemoteViewsProxyService,
                    widgetId,
                    widgetIntent,
                    object : IServiceConnection.Stub() {
                        override fun connected(
                            name: ComponentName?,
                            service: IBinder?,
                            dead: Boolean,
                        ) {
                            wrapped = if (dead) {
                                null
                            } else {
                                IRemoteViewsFactory.Stub.asInterface(service)
                            }
                        }
                    },
                    0,
                )
            }

            override fun onDataSetChanged() {
                wrapped?.onDataSetChanged()
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
                return wrapped?.getItemId(position) ?: -1L
            }

            override fun hasStableIds(): Boolean {
                return wrapped?.hasStableIds() == true
            }

            override fun isCreated(): Boolean {
                return wrapped != null && wrapped?.isCreated == true
            }

            override fun getRemoteCollectionItems(
                capSize: Int,
                capBitmapSize: Int
            ): RemoteViews.RemoteCollectionItems? {
                return wrapped?.getRemoteCollectionItems(capSize, capBitmapSize)
            }

            override fun onDestroy(intent: Intent?) {
                wrapped?.onDestroy(widgetIntent)
                sFactories.remove(widgetId)
            }

            override fun onDataSetChangedAsync() {
                wrapped?.onDataSetChangedAsync()
            }
        }

        sFactories[widgetId] = newFactory

        return newFactory
    }

    companion object {
        const val EXTRA_INTENT = "intent"

        private val sFactories = hashMapOf<Int, IRemoteViewsFactory.Stub>()

        fun createProxyIntent(context: Context, widgetId: Int, widgetIntent: Intent?): Intent {
            val intent = Intent(context, RemoteViewsProxyService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            intent.putExtra(EXTRA_INTENT, widgetIntent)

            return intent
        }
    }
}
