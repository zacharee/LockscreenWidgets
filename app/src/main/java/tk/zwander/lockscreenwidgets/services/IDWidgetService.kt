package tk.zwander.lockscreenwidgets.services

import android.content.Intent
import android.widget.RemoteViewsService
import tk.zwander.lockscreenwidgets.appwidget.IDWidgetFactory

class IDWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return IDWidgetFactory(this)
    }
}