package tk.zwander.common.data.window

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WidgetStackStyle(
    val showButtonBackground: Boolean = true,
    val roundedCorners: Boolean = true,
) : Parcelable
