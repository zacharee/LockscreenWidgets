package tk.zwander.lockscreenwidgets.services

import android.content.Intent
import android.widget.RemoteViewsService
import tk.zwander.lockscreenwidgets.appwidget.Factory

class IDWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return Factory(this)
    }
}