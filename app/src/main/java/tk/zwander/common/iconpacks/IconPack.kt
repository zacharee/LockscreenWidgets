package tk.zwander.common.iconpacks

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import java.util.Calendar

data class IconPack(
    private val componentMap: Map<ComponentName, IconEntry>,
    private val calendarMap: Map<ComponentName, IconEntry>,
    private val clockMap: Map<ComponentName, IconEntry>,
    private val clockMetadata: Map<IconEntry, ClockMetadata>,
) {
    private val idCache = mutableMapOf<String, Int>()

    fun resolveIcon(context: Context, componentName: ComponentName): Drawable? {
        val iconEntry = componentMap[componentName] ?: return null
        val clockMetadata = clockMetadata[iconEntry]

        var resolvedEntry = iconEntry

        when {
            iconEntry.type == IconType.Calendar -> {
                val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                resolvedEntry = iconEntry.resolveDynamicCalendar(dayOfMonth)
            }
        }

        val drawable = getIconDrawable(context, resolvedEntry)

        if (clockMetadata != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val wrapper = ClockDrawableWrapper.forMeta(clockMetadata) {
                drawable
            }

            return wrapper?.let { AdaptiveIconDrawable(wrapper.background, wrapper.foreground) }
        }

        return drawable
    }

    @SuppressLint("DiscouragedApi")
    private fun getIconDrawable(context: Context, entry: IconEntry): Drawable? {
        val packResources = try {
            context.packageManager.getResourcesForApplication(entry.packPackageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }

        val drawableId = idCache.getOrPut(entry.name) {
            packResources.getIdentifier(entry.name, "drawable", entry.packPackageName)
        }

        return ResourcesCompat.getDrawable(packResources, drawableId, context.theme)
    }
}
