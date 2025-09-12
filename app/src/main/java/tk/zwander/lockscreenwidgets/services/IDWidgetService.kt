package tk.zwander.lockscreenwidgets.services

import android.content.Intent
import android.widget.RemoteViewsService
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.eventManager
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.appwidget.IDWidgetFactory

/**
 * Service for the ID list widget for populating its data.
 */
class IDWidgetService : RemoteViewsService(), EventObserver {
    private val factory by lazy { IDWidgetFactory(this) }

    override fun onCreate() {
        super.onCreate()
        eventManager.addObserver(this)
    }

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return factory
    }

    override fun onDestroy() {
        super.onDestroy()
        eventManager.removeObserver(this)
    }

    override fun onEvent(event: Event) {
        when (event) {
            is Event.DebugIdsUpdated -> {
                factory.setItems(event.ids)
                IDListProvider.sendUpdate(this)
            }
            else -> {}
        }
    }
}