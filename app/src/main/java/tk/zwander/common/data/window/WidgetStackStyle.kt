package tk.zwander.common.data.window

import android.content.Context
import android.os.Parcelable
import androidx.annotation.ColorInt
import kotlinx.parcelize.Parcelize
import tk.zwander.common.util.getAttrColor
import tk.zwander.lockscreenwidgets.R

@Parcelize
data class WidgetStackStyle(
    val showButtonBackground: Boolean = true,
    val roundedCorners: Boolean = true,
    @ColorInt
    val iconColor: Int? = null,
) : Parcelable {
    @ColorInt
    fun getIconColor(context: Context): Int {
        return iconColor ?: context.getAttrColor(R.attr.colorOnPrimary)
    }
}
