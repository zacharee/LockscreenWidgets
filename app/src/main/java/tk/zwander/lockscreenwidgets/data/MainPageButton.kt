package tk.zwander.lockscreenwidgets.data

import android.annotation.DrawableRes
import android.annotation.StringRes

data class MainPageButton(
    @DrawableRes
    val icon: Int,
    @StringRes
    val title: Int,
    val onClick: () -> Unit
)