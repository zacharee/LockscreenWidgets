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
) : BaseListInfo<WidgetListInfo>(
    widgetName, previewImg, appInfo
) {
    override fun compareTo(other: WidgetListInfo): Int {
        val nameResult = super.compareTo(other)

        return if (nameResult == 0) {
            val widthResult = providerInfo.minWidth.compareTo(other.providerInfo.minWidth)

            if (widthResult == 0) {
                providerInfo.minHeight.compareTo(other.providerInfo.minHeight)
            } else {
                widthResult
            }
        } else {
            nameResult
        }
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) &&
                other is WidgetListInfo &&
                providerInfo.provider == other.providerInfo.provider &&
                providerInfo.configure == other.providerInfo.configure &&
                providerInfo.minWidth == other.providerInfo.minWidth &&
                providerInfo.minHeight == other.providerInfo.minHeight
    }

    override fun hashCode(): Int {
        return super.hashCode() + Objects.hash(
            providerInfo.provider,
            providerInfo.configure,
            providerInfo.minWidth,
            providerInfo.minHeight
        )
    }
}