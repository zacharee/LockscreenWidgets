package tk.zwander.common.compose.data

import android.annotation.DrawableRes
import android.annotation.StringRes

data class MainPageLink(
    @DrawableRes
    val icon: Int,
    @StringRes
    val title: Int,
    @StringRes
    val desc: Int,
    val link: String = "",
    val isEmail: Boolean = false,
    val onClick: (() -> Unit)? = null
)