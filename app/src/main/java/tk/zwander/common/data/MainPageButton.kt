package tk.zwander.common.data

import android.annotation.DrawableRes
import android.annotation.StringRes
import androidx.compose.runtime.Composable

data class MainPageButton(
    @DrawableRes
    val icon: Int,
    @StringRes
    val title: Int,
    val dependency: @Composable () -> Boolean = { true },
    val onClick: () -> Unit,
)