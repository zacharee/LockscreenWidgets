package tk.zwander.common.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Parcelable
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import tk.zwander.common.iconpacks.iconPackManager
import tk.zwander.common.util.base64ToBitmap
import tk.zwander.common.util.density
import tk.zwander.common.util.getRemoteDrawable
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.toBase64
import tk.zwander.common.util.toSafeBitmap
import java.util.Objects

/**
 * Persistent data for a widget added to the frame.
 *
 * @property id the ID of the widget
 */
@Suppress("unused")
@Parcelize
data class WidgetData(
    val id: Int,
    val type: WidgetType? = WidgetType.WIDGET,
    val label: String?,
    val icon: String?,
    @Deprecated("Pass a Bitmap as a Base64 String to [icon] instead.")
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
            icon: String?,
            size: WidgetSizeData?,
        ): WidgetData {
            return WidgetData(
                id, WidgetType.WIDGET, label, icon,
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

    @Suppress("DEPRECATION")
    fun getIconBitmap(context: Context): Bitmap? {
        if (type == WidgetType.SHORTCUT || type == WidgetType.LAUNCHER_SHORTCUT) {
            context.prefManager.shortcutOverrideIcons[id]?.let { overrideIcon ->
                return overrideIcon.base64ToBitmap()
            }


            val iconPackIcon = shortcutIntent?.component?.let {
                context.iconPackManager.currentIconPack.value?.resolveIcon(
                    context,
                    it,
                )
            }

            /*
            // TODO: Maybe make this a toggleable setting?
            // This overrides all shortcut icons with the matching launcher component icon, which
            // might not be desirable.
            ?: (shortcutIntent?.`package` ?: shortcutIntent?.component?.packageName)?.let {
                context.packageManager.queryIntentActivitiesCompat(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        `package` = it
                    }
                ).firstNotNullOfOrNull { resolveInfo ->
                    context.iconPackManager.currentIconPack.value?.resolveIcon(context, resolveInfo.componentInfo.componentName)
                }
            }
             */

            iconPackIcon?.let {
                return iconPackIcon.toSafeBitmap(context.density, maxSize = 128.dp)
            }
        }

        return icon?.base64ToBitmap() ?: iconRes?.run {
            try {
                context.getRemoteDrawable(this.packageName, this).toSafeBitmap(context.density, maxSize = 128.dp)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}

enum class WidgetType {
    WIDGET,
    SHORTCUT,
    HEADER,
    LAUNCHER_SHORTCUT,
}