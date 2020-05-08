package tk.zwander.lockscreenwidgets.data

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ApplicationInfo

data class WidgetListInfo(
    var widgetName: String,
    var previewImg: Int,
    var providerInfo: AppWidgetProviderInfo,
    var appInfo: ApplicationInfo
) : Comparable<WidgetListInfo> {
    override fun compareTo(other: WidgetListInfo) =
        widgetName.compareTo(other.widgetName)
}