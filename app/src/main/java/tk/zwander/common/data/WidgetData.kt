package tk.zwander.common.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Parcelable
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import tk.zwander.common.iconpacks.iconPackManager
import tk.zwander.common.util.base64ToBitmap
import tk.zwander.common.util.density
import tk.zwander.common.util.getRemoteDrawable
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.toSafeBitmap
import tk.zwander.lockscreenwidgets.util.IconPrefs
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
    @Deprecated("Use [IconPrefs] instead.")
    val icon: String?,
    @Deprecated("Use [IconPrefs] instead.")
    val iconRes: Intent.ShortcutIconResource?,
    val shortcutIntent: Intent?,
    val widgetProvider: String?,
    val size: WidgetSizeData?,
    val packageName: String?,
) : Parcelable {
    companion object {
        fun shortcut(
            context: Context,
            id: Int,
            label: String,
            icon: Bitmap?,
            iconRes: Intent.ShortcutIconResource?,
            shortcutIntent: Intent?,
            size: WidgetSizeData,
        ): WidgetData {
            IconPrefs.setIconForWidget(context, id, icon)

            return WidgetData(
                id, WidgetType.SHORTCUT,
                label, null, iconRes, shortcutIntent,
                null, size, null,
            )
        }

        fun widget(
            context: Context,
            id: Int,
            widgetProvider: ComponentName,
            label: String,
            icon: String?,
            size: WidgetSizeData?,
        ): WidgetData {
            IconPrefs.setIconForWidget(context, id, icon)

            return WidgetData(
                id, WidgetType.WIDGET, label, null,
                null, null,
                widgetProvider.flattenToString(),
                size, widgetProvider.packageName,
            )
        }

        fun launcherItem(
            id: Int,
            packageName: String,
            componentName: ComponentName,
            size: WidgetSizeData,
        ): WidgetData {
            return WidgetData(
                id, WidgetType.LAUNCHER_ITEM,
                null, null, null, null,
                componentName.flattenToString(), size, packageName,
            )
        }

        fun launcherShortcut(
            context: Context,
            id: Int,
            label: String,
            icon: String?,
            intent: Intent?,
            size: WidgetSizeData,
        ): WidgetData {
            IconPrefs.setIconForWidget(context, id, icon)

            return WidgetData(
                id, WidgetType.LAUNCHER_SHORTCUT,
                label, null, null, intent,
                null, size, null,
            )
        }
    }

    val safeType: WidgetType
        get() = type ?: WidgetType.WIDGET

    @IgnoredOnParcel
    val widgetProviderComponent: ComponentName? =
        widgetProvider?.let { ComponentName.unflattenFromString(it) }

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

    private fun getOverrideIcon(context: Context): Bitmap? {
        context.prefManager.shortcutOverrideIcons[id]?.let { entry ->
            context.iconPackManager.currentIconPack.value?.resolveEntry(
                context,
                entry,
            )?.let { override ->
                return override.toSafeBitmap(context.density, maxSize = 128.dp)
            }
        }

        return null
    }

    fun getIconBitmap(context: Context): Bitmap? {
        if (type == WidgetType.LAUNCHER_ITEM && packageName != null && widgetProviderComponent != null) {
            getOverrideIcon(context)?.let {
                return it
            }

            return (context.iconPackManager.currentIconPack.value?.resolveIcon(
                context,
                widgetProviderComponent
            ) ?: (try {
                context.packageManager.getActivityIcon(widgetProviderComponent)
            } catch (e: Exception) {
                null
            }) ?: (try {
                context.packageManager.getApplicationIcon(widgetProviderComponent.packageName)
            } catch (e: Exception) {
                null
            }))?.toSafeBitmap(context.density, maxSize = 128.dp)
        }

        if (type == WidgetType.SHORTCUT || type == WidgetType.LAUNCHER_SHORTCUT) {
            getOverrideIcon(context)?.let {
                return it
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

        return IconPrefs.getIconForWidget(context, id) ?: getNonOverriddenIcon(context)
    }

    @Suppress("DEPRECATION")
    fun getNonOverriddenIcon(context: Context): Bitmap? {
        return icon?.base64ToBitmap() ?: iconRes?.run {
            try {
                context.getRemoteDrawable(this.packageName, this)
                    .toSafeBitmap(context.density, maxSize = 128.dp)
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
    LAUNCHER_ITEM,
}