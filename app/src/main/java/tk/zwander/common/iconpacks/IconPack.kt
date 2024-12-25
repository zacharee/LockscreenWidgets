package tk.zwander.common.iconpacks

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import java.util.Calendar

data class IconPack(
    private val componentMap: Map<ComponentName, IconEntry>,
    private val calendarMap: Map<ComponentName, IconEntry>,
    private val clockMap: Map<ComponentName, IconEntry>,
    private val clockMetadata: Map<IconEntry, ClockMetadata>,
) {
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

        return null
    }
}
