package tk.zwander.lockscreenwidgets.data

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ApplicationInfo
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity

/**
 * Hold the info for a widget listed in [AddWidgetActivity]
 *
 * @property widgetName the label of the widget
 * @property previewImg the preview image of the widget
 * @property providerInfo the information about the widget
 * @property appInfo the information about the application this widget belongs to
 */
data class WidgetListInfo(
    var widgetName: String,
    var previewImg: Int,
    var providerInfo: AppWidgetProviderInfo,
    var appInfo: ApplicationInfo
) : Comparable<WidgetListInfo> {
    override fun compareTo(other: WidgetListInfo) =
        widgetName.compareTo(other.widgetName)
}