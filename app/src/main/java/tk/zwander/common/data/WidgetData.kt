package tk.zwander.common.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import tk.zwander.common.util.base64ToBitmap
import tk.zwander.common.util.getRemoteDrawable
import tk.zwander.common.util.toBase64
import java.util.Objects

/**
 * Persistent data for a widget added to the frame.
 *
 * @property id the ID of the widget
 */
@Parcelize
data class WidgetData(
    val id: Int,
    val type: WidgetType? = WidgetType.WIDGET,
    val label: String?,
    val icon: String?,
    val iconRes: Intent.ShortcutIconResource?,
    val shortcutIntent: Intent?,
    val widgetProvider: String?,
    val size: WidgetSizeData?,
) : Parcelable {
    companion object {
        fun shortcut(
            id: Int,
            label: String,
            icon: Bitmap?,
            iconRes: Intent.ShortcutIconResource?,
            shortcutIntent: Intent?,
            size: WidgetSizeData,
        ): WidgetData {
            return WidgetData(
                id, WidgetType.SHORTCUT,
                label, icon?.toBase64(), iconRes, shortcutIntent,
                null, size,
            )
        }

        fun widget(
            id: Int,
            widgetProvider: ComponentName,
            label: String,
            icon: Bitmap?,
            size: WidgetSizeData?,
        ): WidgetData {
            return WidgetData(
                id, WidgetType.WIDGET, label, icon?.toBase64(),
                null, null,
                widgetProvider.flattenToString(),
                size,
            )
        }

        fun launcherShortcut(
            id: Int,
            label: String,
            icon: String?,
            intent: Intent?,
            size: WidgetSizeData,
        ): WidgetData {
            return WidgetData(
                id, WidgetType.LAUNCHER_SHORTCUT,
                label, icon, null, intent,
                null, size,
            )
        }
    }

    val safeType: WidgetType
        get() = type ?: WidgetType.WIDGET

    val widgetProviderComponent: ComponentName?
        get() = widgetProvider?.let { ComponentName.unflattenFromString(it) }

    context(Context)
    val iconBitmap: Bitmap?
        get() = icon?.base64ToBitmap() ?: iconRes?.run {
            try {
                getRemoteDrawable(this.packageName, this)
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }
        }

    val safeSize: WidgetSizeData
        get() = size ?: WidgetSizeData(1, 1)

    override fun equals(other: Any?): Boolean {
        return other is WidgetData && other.id == id
                && other.safeType == safeType
                && (safeType != WidgetType.WIDGET || widgetProviderComponent == other.widgetProviderComponent)
    }

    override fun hashCode(): Int {
        return if (safeType != WidgetType.WIDGET) {
            Objects.hash(id, safeType)
        } else {
            Objects.hash(id, safeType, widgetProviderComponent)
        }
    }
}

enum class WidgetType {
    WIDGET,
    SHORTCUT,
    HEADER,
    LAUNCHER_SHORTCUT,
}