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
 * @property appInfo the information about the application this widget belongs to
 * @property itemInfo the information about the widget
 */
class WidgetListInfo(
    widgetName: String,
    previewImg: Int,
    appInfo: ApplicationInfo,
    itemInfo: AppWidgetProviderInfo,
) : BaseListInfo<AppWidgetProviderInfo, Int>(
    widgetName, previewImg, appInfo, itemInfo,
) {
    override fun compareTo(other: BaseListInfo<AppWidgetProviderInfo, Int>): Int {
        val nameResult = super.compareTo(other)

        return if (nameResult == 0) {
            val widthResult = itemInfo.minWidth.compareTo(other.itemInfo.minWidth)

            if (widthResult == 0) {
                itemInfo.minHeight.compareTo(other.itemInfo.minHeight)
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
                itemInfo.provider == other.itemInfo.provider &&
                itemInfo.configure == other.itemInfo.configure &&
                itemInfo.minWidth == other.itemInfo.minWidth &&
                itemInfo.minHeight == other.itemInfo.minHeight
    }

    override fun hashCode(): Int {
        return super.hashCode() + Objects.hash(
            itemInfo.provider,
            itemInfo.configure,
            itemInfo.minWidth,
            itemInfo.minHeight
        )
    }
}