package tk.zwander.lockscreenwidgets.services

import android.content.Intent
import android.widget.RemoteViewsService
import tk.zwander.lockscreenwidgets.appwidget.IDWidgetFactory

/**
 * Service for the ID list widget for populating its data.
 */
class IDWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return IDWidgetFactory(this)
    }
}