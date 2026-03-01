package tk.zwander.common.compose.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import tk.zwander.common.data.MainPageButton
import tk.zwander.common.util.EventObserver
import tk.zwander.lockscreenwidgets.R

data class FeatureCardInfo(
    @StringRes
    val title: Int,
    @StringRes
    val enabledLabel: Int? = null,
    @StringRes
    val disabledLabel: Int? = null,
    val enabledKey: String? = null,
    val buttons: List<MainPageButton> = listOf(),
    val onAction: () -> Unit,
    val isEnabled: () -> Boolean = { true },
    val onEnabledChanged: (Boolean) -> Unit = {},
    val eventObserver: EventObserver? = null,
    @StringRes
    val actionButtonTextRes: Int = R.string.add_widget,
    @DrawableRes
    val actionButtonIconRes: Int? = R.drawable.ic_baseline_add_24,
)