package tk.zwander.common.util

import android.appwidget.AppWidgetManager
import android.content.Context
import tk.zwander.lockscreenwidgets.util.safeApplicationContext

val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(safeApplicationContext)
