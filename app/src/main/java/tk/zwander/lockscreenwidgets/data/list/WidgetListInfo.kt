package tk.zwander.lockscreenwidgets.data.list

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ApplicationInfo
import tk.zwander.common.activities.add.AddWidgetActivity
import java.util.*

/**
 * Hold the info for a widget listed in [AddWidgetActivity]
 *
 * @property widgetName the label of the widget
 * @property previewImg the preview image of the widget
 * @property providerInfo the information about the widget
 * @property appInfo the information about the application this widget belongs to
 */
class WidgetListInfo(
    widgetName: String,
    previewImg: Int,
    var providerInfo: AppWidgetProviderInfo,
    appInfo: ApplicationInfo
) : BaseListInfo(
    widgetName, previewImg, appInfo
) {
    override fun equals(other: Any?): Boolean {
        return super.equals(other) &&
                providerInfo.provider == (other as WidgetListInfo).providerInfo.provider
    }

    override fun hashCode(): Int {
        return super.hashCode() + Objects.hash(providerInfo.provider)
    }
}