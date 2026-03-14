package tk.zwander.common.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import tk.zwander.lockscreenwidgets.R

@Parcelize
data class WidgetListFilters(
    val currentCategories: List<Category> = listOf(),
) : Parcelable {
    enum class Category(val labelRes: Int) {
        WIDGETS(R.string.filter_widgets),
        SHORTCUTS(R.string.filter_shortcuts),
        LAUNCHERS(R.string.filter_launcher_icons),
    }
}
