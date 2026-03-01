package tk.zwander.lockscreenwidgets.data.list

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.IconCompat
import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.data.BaseAppInfo
import java.util.Objects

/**
 * Hold the info for a widget listed in [AddWidgetActivity]
 *
 * @param widgetName the label of the widget
 * @param previewImg the preview image of the widget
 * @param appInfo the information about the application this widget belongs to
 * @param itemInfo the information about the widget
 */
class WidgetListInfo(
    widgetName: String,
    previewImg: IconCompat?,
    appInfo: BaseAppInfo<*>,
    itemInfo: AppWidgetProviderInfo,
    profileIcon: Drawable?,
) : BaseListInfo<AppWidgetProviderInfo>(
    widgetName, previewImg, appInfo, itemInfo, profileIcon,
) {
    override fun compareTo(other: BaseListInfo<AppWidgetProviderInfo>): Int {
        val nameResult = super.compareTo(other)

        return if (nameResult == 0) {
            val widthResult = itemInfo.minWidth.compareTo(other.itemInfo.minWidth)

            if (widthResult == 0) {
                val heightResult = itemInfo.minHeight.compareTo(other.itemInfo.minHeight)

                if (heightResult == 0) {
                    itemInfo.profile.identifier.compareTo(other.itemInfo.profile.identifier)
                } else {
                    heightResult
                }
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
                itemInfo.minHeight == other.itemInfo.minHeight &&
                itemInfo.profile == other.itemInfo.profile
    }

    override fun hashCode(): Int {
        return super.hashCode() + Objects.hash(
            itemInfo.provider,
            itemInfo.configure,
            itemInfo.minWidth,
            itemInfo.minHeight,
            itemInfo.profile,
        )
    }
}