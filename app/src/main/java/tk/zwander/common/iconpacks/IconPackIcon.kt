package tk.zwander.common.iconpacks

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class IconPackIcon(
    val name: String,
    val component: ComponentName?,
    val loadDrawable: () -> Drawable?,
) : Comparable<IconPackIcon> {
    override fun compareTo(other: IconPackIcon): Int {
        return name.compareTo(other.name, true)
    }
}
