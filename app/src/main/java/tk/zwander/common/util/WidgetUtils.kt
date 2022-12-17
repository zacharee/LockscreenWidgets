package tk.zwander.common.util

import android.appwidget.AppWidgetManager
import android.content.Context

val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(safeApplicationContext)
